#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsNode(toolsImage: 'stakater/builder-maven:3.5.4-jdk1.8-apline8-v0.0.3') {

        def builder = new io.stakater.builder.Build()
        def docker = new io.stakater.containers.Docker()
        def stakaterCommands = new io.stakater.StakaterCommands()
        def git = new io.stakater.vc.Git()
        def slack = new io.stakater.notifications.Slack()
        def common = new io.stakater.Common()
        def utils = new io.fabric8.Utils()
        def templates = new io.stakater.charts.Templates()

        // Slack variables
        def slackChannel = "${env.SLACK_CHANNEL}"
        def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

        def dockerRegistryURL = config.dockerRegistryURL ?: common.getEnvValue('DOCKER_REGISTRY_URL')
        def performanceTestsJob = config.performanceTestJob ?: ""
        def dockerImage = ""
        def version = ""

        container(name: 'tools') {
            withCurrentRepo() { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                def kubernetesDir = WORKSPACE + "/deployments/kubernetes"
                def chartTemplatesDir = kubernetesDir + "/templates/chart"
                def chartDir = kubernetesDir + "/chart"
                def manifestsDir = kubernetesDir + "/manifests"

                def imageName = repoName.split("dockerfile-").last().toLowerCase()
                def fullAppNameWithVersion = ""
                echo "Image NAME: ${imageName}"
                if (repoOwner.startsWith('stakater-')){
                    repoOwner = 'stakater'
                }
                echo "Repo Owner: ${repoOwner}" 
                try {
                    stage('Create Version'){
                        dockerImage = "${dockerRegistryURL}/${repoOwner.toLowerCase()}/${imageName}"
                        // If image Prefix is passed, use it, else pass empty string to create versions
                        def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''
                        version = stakaterCommands.createImageVersionForCiAndCd(imagePrefix, "${env.BRANCH_NAME}", "${env.BUILD_NUMBER}")
                        echo "Version: ${version}"
                        fullAppNameWithVersion = imageName + '-'+ version
                    }
                    stage('Build Maven Application') {
                        echo "Building Maven application"   
                        builder.buildMavenApplication(fullAppNameWithVersion)
                    }                    
                    stage('Image build & push') {
                        sh """
                            export DOCKER_IMAGE=${dockerImage}
                            export DOCKER_TAG=${version}
                        """
                        docker.buildImageWithTagCustom(dockerImage, version)
                        docker.pushTagCustom(dockerImage, version)
                    }
                    stage('Publish Charts, Manifests'){
                        echo "Rendering Chart & generating manifests"
                        // Render chart from templates
                        templates.renderChart(chartTemplatesDir, chartDir, repoName.toLowerCase(), version, dockerImage)
                        // Generate manifests from chart
                        templates.generateManifests(chartDir, repoName.toLowerCase(), manifestsDir)
                    }                    
                    stage('Run Synthetic Tests') {          
                        echo "Running synthetic tests for Maven application"
                        
                        carbookE2ETestStage([
                            microservice: [
                                    name   : "carbook",
                                    version: version
                            ]
                        ])
                    
                    }
                    stage('Deploy chart'){
                        echo "Deploying Chart for PR"   
                        builder.deployHelmChartForPR(chartDir)
                    }
                    stage('Run Performance Tests') {
                        echo "Running Performance tests for Maven application"
                        if (performanceTestsJob.equals("")){
                            echo "Running performance tests from Makefile"                           
                            builder.runPerformanceTestsForMavenApplication()
                        }else{
                            build job: performanceTestsJob
                        }
                    }                  
                }
                catch (e) {
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                    def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }

                stage('Notify') {
                    slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")])

                    def commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"
                    git.addCommentToPullRequest(commentMessage)
                }
            }
        }
    }
}

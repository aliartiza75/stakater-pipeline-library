#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()
    def utils = new Utils()

    def defaultLabel = buildId('ui')
    def label = parameters.get('label', defaultLabel)

    def uiImage = parameters.get('uiImage', 'fabric8/fabric8-ui-builder:0.0.8')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = 'jenkinsci/jnlp-slave:2.62'

    def cloud = flow.getCloudConfig()

    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
        echo 'Mounting docker socket to build docker images'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 'ui',
                                image: "${uiImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                privileged: true,
                                workingDir: '/home/jenkins/',
                                ttyEnabled: true,
                                envVars: [
                                        envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')
                                ]
                        )
                ],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
                ]
        ) {
            body()
        }
    } else {
        echo 'Mounting docker socket to build docker images'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'ui', image: "${uiImage}", command: '/bin/sh -c', args: 'cat', privileged: true,  workingDir: '/home/jenkins/', ttyEnabled: true, envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]]],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        //secretVolume(secretName: 'npm-npmrc', mountPath: '/home/jenkins/.npm-npmrc'),
                        //secretVolume(secretName: 'npm-token', mountPath: '/home/jenkins/.npm-token'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
                envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
            body()
        }
    }
}

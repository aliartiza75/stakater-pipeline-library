def call(Map parameters = [:], body) {
    withSCM { def repoUrl, def repoName, def repoOwner, def repoBranch, def repoCloneBranch ->
        print "Repo URL ${repoUrl}"
        print "Repo Name ${repoName}"
        print "Repo Branch ${repoBranch}"
        print "Repo Owner ${repoOwner}"
        print "Current Change Branch ${repoCloneBranch}"

        def gitUsername = parameters.get('gitUsername', 'stakater-user')
        def gitEmail = parameters.get('gitEmail', 'stakater@gmail.com')
        def cloneUsingToken = parameters.get('useToken', false)
        def tokenSecretName = parameters.get('tokenSecretName',"")

        def workspaceDir = "/home/jenkins/" + repoName
        sh "mkdir -p ${workspaceDir}"

        def type = parameters.get('type', '')

        switch(type.toLowerCase()) {
            // Symlink workspaceDir in gopath if go project
            case "go":
                def host = repoUrl.substring(repoUrl.indexOf("@") + 1, repoUrl.indexOf(":"))
                def symlinkDir = "/go/src/${host}/${repoOwner}"
                def symlink = "${symlinkDir}/${repoName}"
                println("Creating symlink of ${workspaceDir} at ${symlink} for go build")
                sh """
                    mkdir -p ${symlinkDir}
                    ln -s ${workspaceDir} ${symlink}
                """
            break
        }

        def git = new io.stakater.vc.Git()

        git.setUserInfo(gitUsername, gitEmail)

        if(cloneUsingToken){
            git.checkoutRepoUsingToken(gitUsername, tokenSecretName, repoUrl, repoCloneBranch, workspaceDir)
        } else {
            git.checkoutRepoUsingTokenWithDefaults(gitUsername, repoUrl, repoCloneBranch, workspaceDir)
        }

        ws(workspaceDir) {
            body(repoUrl, repoName, repoOwner, repoCloneBranch)            
        }
    }
}

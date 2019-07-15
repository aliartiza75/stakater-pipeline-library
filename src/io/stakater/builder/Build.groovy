#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(String version, String mavenGoal = "clean package"){
    sh """
        mvn versions:set -DnewVersion=${version} -f pom.xml
        mvn ${mavenGoal} -f pom.xml
    """
}

def buildGradleApplication(String version, String gradleGoal = "clean package"){
    sh """
        gradle -Pversion=${version} ${gradleGoal}
    """
}

def buildAspNetApplication(){
    sh """
        dotnet restore
        dotnet publish -c Release -o out
    """
}

def buildNodeApplication(String version) {
    sh """
        npm --no-git-tag-version --allow-same-version version ${version}
        npm install
    """
}

def deployHelmChart(String chartDir){
    sh """        
        make install-chart
    """
}

return this
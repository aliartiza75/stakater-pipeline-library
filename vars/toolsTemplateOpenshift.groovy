#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    def toolsImage = parameters.get('toolsImage', 'stakater/pipeline-tools:v2.0.5')
    String serviceAccount = parameters.get('serviceAccount', 'jenkins')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    echo 'Using toolsImage : ' + toolsImage
    echo 'Using serviceAccount : ' + serviceAccount
    echo 'Mounting docker socket to build docker images'

    podTemplate(cloud: cloud, label: label, serviceAccount: serviceAccount, inheritFrom: "${inheritFrom}",
        annotations: [
          podAnnotation(key: "scheduler.alpha.kubernetes.io/critical-pod", value: "true")
        ],
        containers: [
          containerTemplate(
            name: 'tools',
            image: "${toolsImage}",
            command: '/bin/sh -c',
            args: 'cat',
            privileged: true,
            workingDir: '/home/jenkins/',
            ttyEnabled: true
          )],
        envVars: [
          secretEnvVar(key: 'NEXUS_USERNAME', secretName: 'nexus-auth', secretKey: 'username'),
          secretEnvVar(key: 'NEXUS_PASSWORD', secretName: 'nexus-auth', secretKey: 'password'),
        ],
        volumes: [
          secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
          secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
          hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
        ]) {
      body.call(label)
    }
}
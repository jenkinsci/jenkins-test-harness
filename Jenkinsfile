pipeline {
    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 1, unit: 'HOURS')
    }
    agent {
        docker {
            image 'maven:3.5.0-jdk-8'
            label 'docker'
        }
    }
    stages {
        stage('main') {
            // TODO Windows build in parallel
            steps {
                sh 'mvn -B clean verify -Dset.changelist'
            }
            post {
                success {
                    junit '**/target/surefire-reports/TEST-*.xml'
                    dir("$HOME/.m2/repository") {
                        archiveArtifacts 'org/jenkins-ci/main/jenkins-test-harness/*-rc*/'
                    }
                    script {
                        infra.maybePublishIncrementals()
                    }
                }
            }
        }
    }
}

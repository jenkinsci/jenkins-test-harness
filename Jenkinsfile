properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('docker') {
    timeout(time: 1, unit: 'HOURS') {
        deleteDir()
        checkout scm
        def tmp = pwd tmp: true
        // TODO or can do explicitly something like: docker run -v "$TMP"/m2repo:/var/maven/.m2/repository --rm -u $(id -u):$(id -g) -e MAVEN_CONFIG=/var/maven/.m2 -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven maven:3.6.1-jdk-8 mvn -Duser.home=/var/maven …
        docker.image('maven:3.6.1-jdk-8').inside {
            withEnv(["TMP=$tmp"]) {
                // TODO Azure mirror
                sh 'mvn -B -Dmaven.repo.local="$TMP"/m2repo -ntp -e -Dset.changelist -Dexpression=changelist -Doutput="$TMP"/changelist -Dmaven.test.failure.ignore help:evaluate clean install'
            }
        }
        junit '**/target/surefire-reports/TEST-*.xml'
        def changelist = readFile("$tmp/changelist")
        dir("$tmp/m2repo") {
            archiveArtifacts "**/*$changelist/*$changelist*"
        }
    }
}
infra.maybePublishIncrementals()

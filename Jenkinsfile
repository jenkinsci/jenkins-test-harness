properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('maven') {
    timeout(time: 1, unit: 'HOURS') {
        checkout scm
        // TODO Azure mirror
        sh 'mvn -B -ntp -e -Dset.changelist -Dmaven.test.failure.ignore clean install'
        junit '**/target/surefire-reports/TEST-*.xml'
        infra.prepareToPublishIncrementals()
    }
}
infra.maybePublishIncrementals()

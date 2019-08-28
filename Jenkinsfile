// TODO move to infra.groovy once tested
/**
 * Record artifacts created by this build which could be published via Incrementals (JEP-305).
 * Call at most once per build, on a Linux node, after running mvn -Dset.changelist install.
 * Follow up with #maybePublishIncrementals.
 */
def prepareToPublishIncrementals() {
    // MINSTALL-126 would make this easier by letting us publish to a different directory to begin with:
    def m2repo = sh script: 'mvn -Dset.changelist -Dexpression=settings.localRepository -q -DforceStdout help:evaluate', returnStdout: true
    // No easy way to load both of these in one command: https://stackoverflow.com/q/23521889/12916
    def version = sh script: 'mvn -Dset.changelist -Dexpression=project.version -q -DforceStdout help:evaluate', returnStdout: true
    echo "Collecting $version from $m2repo for possible Incrementals publishing"
    dir(m2repo) {
        archiveArtifacts "**/$version/*$version*"
    }
}

properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('maven') {
    timeout(time: 1, unit: 'HOURS') {
        deleteDir()
        checkout scm
        // TODO Azure mirror
        sh 'mvn -B -ntp -e -Dset.changelist -Dmaven.test.failure.ignore help:evaluate clean install'
        junit '**/target/surefire-reports/TEST-*.xml'
        prepareToPublishIncrementals()
    }
}
infra.maybePublishIncrementals()

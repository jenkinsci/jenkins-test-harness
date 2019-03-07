properties([buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '3')), durabilityHint('PERFORMANCE_OPTIMIZED')])


def buildTypes = ['Linux']//, 'Windows']
def jdks = [8, 11]

def builds = [:]
for (i = 0; i < buildTypes.size(); i++) {
    for (j = 0; j < jdks.size(); j++) {
        def buildType = buildTypes[i]
        def jdk = jdks[j]

        builds["${buildType}-jdk${jdk}"] = {
            node(buildType.toLowerCase()) {
                timestamps {
                    deleteDir()
                    stage('Checkout') {
                        checkout scm
                    }

                    def m2repo = "${pwd tmp: true}/m2repo"

                    stage("${buildType} Build / Test") {
                        timeout(time: 60, unit: 'MINUTES') {
                            def mvnCmd = "mvn -V -e -U -s settings-azure.xml clean verify -DskipTests" // FIXME: enable tests once Jenkinsfile OK

                            withMaven(jdk: 'jdk${jdk}', maven: 'mvn') {

                                if (isUnix()) {
                                    sh mvnCmd
                                } else {
                                    bat mvnCmd
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

parallel builds

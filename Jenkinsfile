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
                    // First stage is actually checking out the source. Since we're using Multibranch
                    // currently, we can use "checkout scm".
                    stage('Checkout') {
                        checkout scm
                    }

                    def changelistF = "${pwd tmp: true}/changelist"
                    def m2repo = "${pwd tmp: true}/m2repo"

                    // Now run the actual build.
                    stage("${buildType} Build / Test") {
                        timeout(time: 60, unit: 'MINUTES') {
                            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m",
                                          "MAVEN_OPTS=-Xmx1536m -Xms512m"], jdk) {
                                def mvnCmd = "mvn -V -e -U -s settings-azure.xml clean verify -DskipTests" // FIXME: enable tests once Jenkinsfile OK

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

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

// This method sets up the Maven and JDK tools, puts them in the environment along
// with whatever other arbitrary environment variables we passed in, and runs the
// body we passed in within that environment.
void withMavenEnv(List envVars = [], def javaVersion, def body) {
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String mvntool = tool name: "mvn", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "jdk${javaVersion}", type: 'hudson.model.JDK'

    // Set JAVA_HOME, MAVEN_HOME and special PATH variables for the tools we're
    // using.
    List mvnEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}", "MAVEN_HOME=${mvntool}"]

    // Add any additional environment variables.
    mvnEnv.addAll(envVars)

    // Invoke the body closure we're passed within the environment we've created.
    withEnv(mvnEnv) {
        body.call()
    }
}

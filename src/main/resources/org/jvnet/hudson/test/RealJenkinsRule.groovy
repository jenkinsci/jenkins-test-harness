import jenkins.model.Jenkins
URL[] urls = [new URL(System.getProperty('RealJenkinsRule.location'))]
def run = new URLClassLoader(urls, ClassLoader.systemClassLoader.parent).loadClass('org.jvnet.hudson.test.RealJenkinsRule$Body').getMethod('run', Object)
Jenkins j = Jenkins.instance
def invokeLater = {
    jenkins.util.Timer.get().schedule({ // JENKINS-37807
        if (j.servletContext.getAttribute(/* WebAppMain.APP */"app") instanceof Jenkins) {
            run.invoke(null, j)
        } else {
            invokeLater()
        }
    }, 100L, java.util.concurrent.TimeUnit.MILLISECONDS)
}
invokeLater()

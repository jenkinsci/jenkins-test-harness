package org.jvnet.hudson.test;

import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import java.io.IOException;

/** 
 * {@link PluginManager} to speed up unit tests.
* 
*
* <p>
* Instead of loading every plugin for every test case, this allows them to reuse a single plugin manager.
* This has the downside that you can not disabling plugins.
* <p>
* TODO: {@link Plugin} start/stop/postInitialize invocation semantics gets different. Perhaps
* 
* @author Kohsuke Kawaguchi
* @see HudsonTestCase#useLocalPluginManager
*/
public class TestPluginManager extends UnitTestSupportingPluginManager {

    public static final PluginManager INSTANCE;

    static {
        try {
            INSTANCE = new TestPluginManager();
            Runtime.getRuntime().addShutdownHook(new Thread("delete " + INSTANCE.rootDir) {
                @Override public void run() {
                    // Shutdown and release plugins as in PluginManager#stop
                    ((TestPluginManager)INSTANCE).reallyStop();

                    // allow JVM cleanup handles of jar files...
                    System.gc();

                    try {
                        Util.deleteRecursive(INSTANCE.rootDir);
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public TestPluginManager() throws IOException {
        super(Util.createTempDir());
    }

    // Overwrite PluginManager#stop, not to release plugins in each tests.
    // Releasing plugins result fail to access files in webapp directory in following tests.
    @Override
    public void stop() {
        for (PluginWrapper p : activePlugins)
            p.stop();
    }

    /**
     * As we don't actually shut down classloaders, we instead provide this method that does
     * what {@link PluginManager#stop()} normally does.
     */
    private void reallyStop() {
        super.stop();
    }

}
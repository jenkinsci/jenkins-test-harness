package org.jvnet.hudson.test.injected;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.PluginWrapper;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Various tests injected via the <code>maven-hpi-plugin</code>.
 */
public class OtherTest extends InjectedTest {

    @Test
    void testCliSanity() {
        CLICommand.clone("help");
    }

    @Test
    @WithJenkins
    void testPluginActive() {
        String plugin = artifactId;
        if (plugin != null) {
            if (!Jenkins.get().getPluginManager().getFailedPlugins().isEmpty()) {
                fail("While testing " + plugin + " the following plugins failed to start: \n"
                        + String.join(
                                "\n",
                                Jenkins.get().getPluginManager().getFailedPlugins().stream()
                                        .map(fp -> "\t" + fp.name + " - " + fp.cause)
                                        .toList()));
            }

            PluginWrapper pw = Jenkins.get().getPluginManager().getPlugin(plugin);

            assertNotNull(pw, plugin + " failed to start");
            assertTrue(pw.isActive(), plugin + " was not active");
        }
    }
}

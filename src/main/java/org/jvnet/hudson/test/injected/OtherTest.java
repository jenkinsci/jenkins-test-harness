package org.jvnet.hudson.test.injected;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginWrapper;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Various tests injected via the <code>maven-hpi-plugin</code>.
 */
@WithJenkins
public class OtherTest extends InjectedTest {

    @SuppressWarnings("unused")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private JenkinsRule jenkinsRule;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    @WithoutJenkins
    void testCliSanity() {
        CLICommand.clone("help");
    }

    @Test
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

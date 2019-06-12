package org.jvnet.hudson.test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public class RestartableJenkinsRuleTest {

    @Rule public RestartableJenkinsRule noPortReuse = new RestartableJenkinsRule();

    @Rule
    public RestartableJenkinsRule portReuse =
            new RestartableJenkinsRule.Builder().withReusedPort().build();

    @Test
    public void testNoPortReuse() throws Exception {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        noPortReuse.then(
                s -> {
                    port.set(noPortReuse.j.getURL().getPort());
                });
        noPortReuse.then(
                s -> {
                    assertNotEquals(port.get(), noPortReuse.j.getURL().getPort());
                });
    }

    @Test
    public void testPortReuse() throws Exception {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        portReuse.then(
                s -> {
                    port.set(portReuse.j.getURL().getPort());
                });
        portReuse.then(
                s -> {
                    assertEquals(port.get(), portReuse.j.getURL().getPort());
                });
    }
}

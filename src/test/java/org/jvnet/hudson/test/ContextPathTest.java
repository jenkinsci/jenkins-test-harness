package org.jvnet.hudson.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class ContextPathTest {
    @Rule
    public RealJenkinsRule rjr = new RealJenkinsRule().withPrefix("");

    @Test
    public void test() throws Throwable {
        rjr.then(ContextPathTest::_test);
    }

    public static void _test(JenkinsRule jr) throws Exception {
        assertThat(jr.contextPath, is(""));
    }
}
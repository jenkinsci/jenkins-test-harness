package org.jvnet.hudson.main;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.util.List;

public class JenkinsRuleAllHostInterfaceTest extends BasicTestCase {

    @Rule public JenkinsRule j = new JenkinsRule() {
        {
            host = null;
        }
    };

    @Test 
    public void allInterfaceJenkins() throws Throwable {
        System.out.println("running in: " + j.jenkins.getRootUrl());
        URL jenkinsURL = new URL(j.jenkins.getRootUrl());

        assertEquals("localhost", jenkinsURL.getHost());
    }

    @Test
    public void allInterfaceJenkinsBuild() throws Throwable {
        meat(j);
    }
}

package org.jvnet.hudson.main;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.List;

public class JenkinsRuleHostIPTest extends BasicTestCase {

    private static String HOST_IP = getHostAddress();

    private static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Rule public JenkinsRule j = new JenkinsRule() {
        {
            host = HOST_IP;
        }
    };

    @Test 
    public void hostIpJenkins() throws Throwable {
        System.out.println("running in: " + j.jenkins.getRootUrl());
        URL jenkinsURL = new URL(j.jenkins.getRootUrl());

        assertEquals(HOST_IP, jenkinsURL.getHost());
    }

    @Test
    public void hostIpJenkinsBuild() throws Throwable {
        meat(j);
    }
}

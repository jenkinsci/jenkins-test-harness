package org.jvnet.hudson.test;

import java.io.IOException;
import java.util.HashMap;

import javax.annotation.CheckForNull;

import org.junit.Rule;
import org.junit.Test;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;

import net.sf.json.JSONObject;

import hudson.model.RootAction;

/**
 * Testing JSON cli utilities in JenkinsRule
 */
public class JenkinsRuleJsonCliTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void simpleGetShouldWork() throws IOException {

        JenkinsRule.JSONWebResponse response = j.getJSON("testing-cli/getMe");
        System.out.println("Response:" + response.getContentAsString());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        response = j.getJSON("testing-cli/getError500", wc);
        System.out.println("Response:" + response.getContentAsString());
    }

    @TestExtension
    public static class JsonAPIForTests implements RootAction {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "testing-cli";
        }

        @GET
        @WebMethod(name = "getMe")
        public JsonHttpResponse getMe() {
            HashMap objectForResponse = new HashMap();
            objectForResponse.put("key","I am JenkinsRule");
            return new JsonHttpResponse(JSONObject.fromObject(new MyJsonObject("I am JenkinsRule")), 200);
        }

        @GET
        @WebMethod(name = "getError500")
        public JsonHttpResponse getError500() {
            HashMap objectForResponse = new HashMap();
            objectForResponse.put("key","You got an error 500");
            JsonHttpResponse error500 = new JsonHttpResponse(JSONObject.fromObject(objectForResponse), 500);
            return error500;
        }

    }

    public static class MyJsonObject {
        private String message;

        public MyJsonObject(String message) {
            this.message = message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}

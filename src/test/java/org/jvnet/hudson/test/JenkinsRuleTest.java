package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;

import hudson.model.RootAction;
import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.PUT;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JenkinsRuleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void assertEqualDataBoundBeansForNullLists() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(null), new SomeClass(null));
    }

    @Test(expected = AssertionError.class)
    public void givenOneNullListAndOneNonnullListAssertShouldFail() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(Collections.emptyList()), new SomeClass(null));
    }

    public static class SomeClass {
        private List<String> someList;

        @DataBoundConstructor
        public SomeClass(List<String> someList) {
            this.someList = someList;
        }

        public List<String> getSomeList() {
            return someList;
        }
    }

    @Test
    public void assertEqualDataBoundBeansWithSetters() throws Exception {
        SomeClassWithSetters l = new SomeClassWithSetters("value1");
        l.setSetterParam("value2");
        l.setterField = "value3";
        SomeClassWithSetters r = new SomeClassWithSetters("value1");
        r.setSetterParam("value2");
        r.setterField = "value3";
        j.assertEqualDataBoundBeans(l, r);
    }

    @Test(expected = AssertionError.class)
    public void assertEqualDataBoundBeansWithSettersFail() throws Exception {
        SomeClassWithSetters l = new SomeClassWithSetters("value1");
        l.setSetterParam("value2");
        l.setterField = "value4";
        SomeClassWithSetters r = new SomeClassWithSetters("value1");
        r.setSetterParam("value3");     // mismatch!
        r.setterField = "value4";
        j.assertEqualDataBoundBeans(l, r);
    }

    @Test(expected = AssertionError.class)
    public void assertEqualDataBoundBeansWithSettersFailForField() throws Exception {
        SomeClassWithSetters l = new SomeClassWithSetters("value1");
        l.setSetterParam("value2");
        l.setterField = "value3";
        SomeClassWithSetters r = new SomeClassWithSetters("value1");
        r.setSetterParam("value2");     // mismatch!
        l.setterField = "value4";
        j.assertEqualDataBoundBeans(l, r);
    }

    @Test
    public void testTokenHelperMethods() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        JenkinsRule.WebClient wc = j.createWebClient();

        User alice = User.getById("alice", true);
        User.getById("bob", true);
        User.getById("charlotte", true);

        makeRequestAndAssertLogin(wc, "anonymous");

        wc.login("alice");
        makeRequestAndAssertLogin(wc, "alice");

        wc = j.createWebClient();
        makeRequestAndAssertLogin(wc, "anonymous");

        wc.withBasicCredentials("alice", "alice");
        makeRequestAndAssertLogin(wc, "alice");

        wc = j.createWebClient();

        // if password is not provided, the login is used for both
        wc.withBasicCredentials("alice");
        makeRequestAndAssertLogin(wc, "alice");

        wc = j.createWebClient();
        makeRequestAndAssertLogin(wc, "anonymous");

        // Alice has no legacy API token
        wc.withBasicCredentials("alice", alice.getProperty(ApiTokenProperty.class).getApiToken());
        makeRequestAndAssertLoginUnauthorized(wc);

        wc.withBasicApiToken("alice");
        makeRequestAndAssertLogin(wc, "alice");

        wc = j.createWebClient();
        makeRequestAndAssertLogin(wc, "anonymous");

        wc.withBasicApiToken("bob");
        makeRequestAndAssertLogin(wc, "bob");

        wc = j.createWebClient();
        wc.withBasicApiToken("charlotte");
        makeRequestAndAssertLogin(wc, "charlotte");
    }

    @Test
    public void getJSONTests() throws Exception {

        // Testing a simple GET that should answer 200 OK and a json
        JenkinsRule.JSONWebResponse response = j.getJSON("testing-cli/getMe");
        assertTrue(response.getContentAsString().contains("I am JenkinsRule"));
        assertEquals(response.getStatusCode(), 200);

        // Testing a simple GET with parameter that should answer 200 OK and a json
        response = j.getJSON("testing-cli/getWithParameters?paramValue=whatelse");
        assertTrue(response.getContentAsString().contains("I am JenkinsRule whatelse"));
        assertEquals(response.getStatusCode(), 200);

        //Testing with a GET that the test expect to raise an server error: we want to be able to assert the status
        JenkinsRule.WebClient webClientAcceptException = j.createWebClient();
        webClientAcceptException.setThrowExceptionOnFailingStatusCode(false);
        response = j.getJSON("testing-cli/getError500", webClientAcceptException);
        assertEquals(response.getStatusCode(), 500);

        //Testing a GET that requires the user to be authenticated
        User admin = User.getById("admin", true);
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(admin);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        // - simple call without authentication should be forbidden
        response = j.getJSON("testing-cli/getMe", webClientAcceptException);
        assertEquals(response.getStatusCode(), 403);

        // - same call but authenticated should be fine
        response = j.getJSON("testing-cli/getMe", webClientAcceptException.withBasicApiToken(admin));
        assertEquals(response.getStatusCode(), 200);

    }

    @Test
    public void putJSONTests() throws Exception {

        JenkinsRule.WebClient webClient = j.createWebClient();
        JenkinsRule.JSONWebResponse response;

        // Testing a simple PUT that should answer 200 OK and return same json
        MyJsonObject objectToSend = new MyJsonObject("Jenkins is the way !");
        response = j.putJSON( "testing-cli/putSomething", JSONObject.fromObject(objectToSend), webClient);
        assertTrue(response.getContentAsString().contains("Jenkins is the way !"));

        //Testing with a PUT that the test expect to raise an server error: we want to be able to assert the status
        webClient.setThrowExceptionOnFailingStatusCode(false);
        response = j.putJSON( "testing-cli/putAndGetError500", JSONObject.fromObject(objectToSend), webClient);
        assertEquals(response.getStatusCode(), 500);

        //Testing a PUT that requires the user to be authenticated
        User admin = User.getById("admin", true);
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(admin);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        // - simple call without authentication should be forbidden
        response = j.putJSON("testing-cli/putSomething", JSONObject.fromObject(objectToSend), webClient);
        assertEquals(response.getStatusCode(), 403);

        // - same call but authenticated should be fine
        response = j.putJSON("testing-cli/putSomething",
                             JSONObject.fromObject(objectToSend),
                             webClient.withBasicApiToken(admin));
        assertEquals(response.getStatusCode(), 200);

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
        public HttpResponse getMe() {
            JSONObject response = JSONObject.fromObject(new MyJsonObject("I am JenkinsRule"));
            throw new JsonHttpResponse(response, 200);
        }

        @GET
        @WebMethod(name = "getWithParameters")
        public HttpResponse getWithParameters(@QueryParameter(required = true) String paramValue) {
            assertNotNull(paramValue);
            JSONObject response = JSONObject.fromObject(new MyJsonObject("I am JenkinsRule " + paramValue));
            return new JsonHttpResponse(response, 200);
        }

        @GET
        @WebMethod(name = "getError500")
        public JsonHttpResponse getError500() {
            JsonHttpResponse error500 = new JsonHttpResponse(
                    JSONObject.fromObject(new MyJsonObject("You got an error 500")), 500);
            throw error500;
        }

        @PUT
        @WebMethod(name = "putSomething")
        public JsonHttpResponse putSomething(@JsonBody MyJsonObject body) {

            JSONObject response = JSONObject.fromObject(body);
            return new JsonHttpResponse(response, 200);
        }

        @PUT
        @WebMethod(name = "putAndGetError500")
        public JsonHttpResponse putAndgetError500(@JsonBody JSONObject body) {
            JsonHttpResponse error500 = new JsonHttpResponse(
                    JSONObject.fromObject(new MyJsonObject("You got an error 500")), 500);
            throw error500;
        }
    }

    public static class MyJsonObject {
        private String message;

        //empty constructor required for JSON parsing.
        public MyJsonObject() {}

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

    private void makeRequestAndAssertLogin(JenkinsRule.WebClient wc, String expectedLogin) throws IOException {
        WebRequest req = new WebRequest(new URL(j.getURL(),"whoAmI/api/json"));
        Page p = wc.getPage(req);
        String pageContent = p.getWebResponse().getContentAsString();
        String loginReceived = (String) JSONObject.fromObject(pageContent).get("name");
        assertEquals(expectedLogin, loginReceived.trim());
    }

    private void makeRequestAndAssertLoginUnauthorized(JenkinsRule.WebClient wc) throws IOException {
        WebRequest req = new WebRequest(new URL(j.getURL(),"whoAmI/api/json"));
        try {
            wc.getPage(req);
            fail();
        }
        catch(FailingHttpStatusCodeException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    public static class SomeClassWithSetters {
        private String ctorParam;
        private String setterParam;
        @DataBoundSetter
        public String setterField;

        @DataBoundConstructor
        public SomeClassWithSetters(String ctorParam) {
            this.ctorParam = ctorParam;
        }

        public String getCtorParam() {
            return ctorParam;
        }

        @DataBoundSetter
        public void setSetterParam(String setterParam) {
            this.setterParam = setterParam;
        }

        public String getSetterParam() {
            return setterParam;
        }
    }
}

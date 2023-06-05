package org.jvnet.hudson.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.RootAction;
import hudson.model.User;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
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
import org.kohsuke.stapler.verb.POST;
import org.kohsuke.stapler.verb.PUT;

public class JenkinsRuleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void readOnlyFileInJenkinsHomeIsRemovedOnDispose() throws Exception {
        Path jenkinsHome = j.jenkins.getRootDir().toPath();
        String prefix = "ee";
        Path gitObjectsDir = jenkinsHome.resolve(Path.of(".git", "objects", prefix));
        Files.createDirectories(gitObjectsDir);

        String fileName = prefix + "-this-is-read-only";
        Path gitObjectsFile = Files.createFile(gitObjectsDir.resolve(Path.of(fileName)));

        /* Windows read only files were not being deleted by temporary folder dispose */
        boolean ok = gitObjectsFile.toFile().setReadOnly();
        assertTrue("Failed to set file " + fileName + " as read only", ok);
        assertFalse("Read only file " + fileName + " is writable", Files.isWritable(gitObjectsFile));

        /* cleanup of JENKINS_HOME should remove the read only file */
    }

    @Test
    public void assertEqualDataBoundBeansForNullLists() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(null), new SomeClass(null));
    }

    @Test(expected = AssertionError.class)
    public void givenOneNullListAndOneNonnullListAssertShouldFail() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(List.of()), new SomeClass(null));
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
        JenkinsRule.JSONWebResponse response = j.getJSON("testing-cli/getMyJsonObject");
        assertThat(response.getContentAsString(), containsString("I am JenkinsRule"));
        assertEquals(response.getStatusCode(), 200);

        // Testing a simple GET with parameter that should answer 200 OK and a json
        response = j.getJSON("testing-cli/getWithParameters?paramValue=whatelse");
        assertThat(response.getContentAsString(), containsString("I am JenkinsRule whatelse"));
        assertEquals(response.getStatusCode(), 200);

        //Testing with a GET that the test expect to raise an server error: we want to be able to assert the status
        JenkinsRule.WebClient webClientAcceptException = j.createWebClient();
        webClientAcceptException.setThrowExceptionOnFailingStatusCode(false);
        response = webClientAcceptException.getJSON("testing-cli/getError500");
        assertEquals(response.getStatusCode(), 500);

        //Testing a GET that requires the user to be authenticated
        User admin = User.getById("admin", true);
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(admin);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        // - simple call without authentication should be forbidden
        response = webClientAcceptException.getJSON("testing-cli/getMyJsonObject");
        assertEquals(response.getStatusCode(), 403);

        // - same call but authenticated should be fine
        response = webClientAcceptException.withBasicApiToken(admin).getJSON("testing-cli/getMyJsonObject");
        assertEquals(response.getStatusCode(), 200);

    }

    @Test
    public void postJSONTests() throws IOException {

        User admin = User.getById("admin", true);
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(admin);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        JenkinsRule.WebClient webClient = j.createWebClient();
        JenkinsRule.JSONWebResponse response;

        // Testing an authenticated POST that should answer 200 OK and return same json
        MyJsonObject objectToSend = new MyJsonObject("Creating a new Object with Json.");
        response = webClient
                        .withBasicApiToken(admin)
                        .postJSON( "testing-cli/create", JSONObject.fromObject(objectToSend));
        assertThat(response.getContentAsString(), containsString("Creating a new Object with Json. - CREATED"));
        assertEquals(response.getStatusCode(), 200);

        // Testing an authenticated POST that return error 500
        webClient.setThrowExceptionOnFailingStatusCode(false);
        response = webClient.postJSON( "testing-cli/createFailure", JSONObject.fromObject(objectToSend));
        assertThat(response.getContentAsString(), containsString("Creating a new Object with Json. - NOT CREATED"));
        assertEquals(response.getStatusCode(), 500);

    }

    @Test
    public void putJSONTests() throws Exception {

        JenkinsRule.WebClient webClient = j.createWebClient();
        JenkinsRule.JSONWebResponse response;

        // Testing a simple PUT that should answer 200 OK and return same json
        MyJsonObject objectToSend = new MyJsonObject("Jenkins is the way !");
        response = webClient.putJSON( "testing-cli/update", JSONObject.fromObject(objectToSend));
        assertThat(response.getContentAsString(), containsString("Jenkins is the way ! - UPDATED"));

        //Testing with a PUT that the test expect to raise an server error: we want to be able to assert the status
        webClient.setThrowExceptionOnFailingStatusCode(false);
        response = webClient.putJSON( "testing-cli/updateFailure", JSONObject.fromObject(objectToSend));
        assertEquals(response.getStatusCode(), 500);
        assertThat(response.getContentAsString(), containsString("Jenkins is the way ! - NOT UPDATED"));

        //Testing a PUT that requires the user to be authenticated
        User admin = User.getById("admin", true);
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(admin);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        // - simple call without authentication should be forbidden due to use of RootAction
        response = webClient.putJSON("testing-cli/update", JSONObject.fromObject(objectToSend));
        assertEquals(response.getStatusCode(), 403);

        // - same call but authenticated should be fine
        response = webClient.withBasicApiToken(admin)
                            .putJSON("testing-cli/update", JSONObject.fromObject(objectToSend));
        assertEquals(response.getStatusCode(), 200);
        assertThat(response.getContentAsString(), containsString("Jenkins is the way ! - UPDATED"));

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
        @WebMethod(name = "getMyJsonObject")
        public HttpResponse getMyJsonObject() {
            JSONObject response = JSONObject.fromObject(new MyJsonObject("I am JenkinsRule"));
            return new JsonHttpResponse(response, 200);
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
        @WebMethod(name = "update")
        public JsonHttpResponse update(@JsonBody MyJsonObject body) {
            body.setMessage(body.getMessage()+" - UPDATED");
            JSONObject response = JSONObject.fromObject(body);
            return new JsonHttpResponse(response, 200);
        }

        @PUT
        @WebMethod(name = "updateFailure")
        public JsonHttpResponse updateFailure(@JsonBody MyJsonObject body) {
            body.setMessage(body.getMessage()+" - NOT UPDATED");
            JsonHttpResponse error500 = new JsonHttpResponse(JSONObject.fromObject(body), 500);
            throw error500;
        }

        @POST
        @WebMethod(name = "create")
        public JsonHttpResponse create(@JsonBody MyJsonObject body) {
            body.setMessage(body.getMessage()+" - CREATED");
            JSONObject response = JSONObject.fromObject(body);
            return new JsonHttpResponse(response, 200);
        }

        @POST
        @WebMethod(name = "createFailure")
        public JsonHttpResponse createFailure(@JsonBody MyJsonObject body) {
            body.setMessage(body.getMessage()+" - NOT CREATED");
            JSONObject response = JSONObject.fromObject(body);
            JsonHttpResponse error500 = new JsonHttpResponse(response, 500);
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

    @Test
    public void serialization() throws Exception {
        j.createSlave("agent", "agent", new EnvVars());
        j.jenkins.save();
    }

    @Test
    public void waitForCompletion() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new SleepBuilder(1000));
        FreeStyleBuild b = p.scheduleBuild2(0).getStartCondition().get();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

}

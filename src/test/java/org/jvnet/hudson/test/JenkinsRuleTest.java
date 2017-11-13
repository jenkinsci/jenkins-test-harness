package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.util.HttpResponses;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JenkinsRuleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void assertEqualDataBoundBeansForNullLists() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(null), new SomeClass(null));
    }

    @Test(expected = AssertionError.class)
    public void givenOneNullListAndOneNonnullListAssertShouldFail() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(Collections.<String>emptyList()), new SomeClass(null));
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

        wc.withBasicCredentials("alice", alice.getProperty(ApiTokenProperty.class).getApiToken());
        makeRequestAndAssertLogin(wc, "alice");

        wc = j.createWebClient();
        makeRequestAndAssertLogin(wc, "anonymous");

        wc.withBasicApiToken("bob");
        makeRequestAndAssertLogin(wc, "bob");

        wc = j.createWebClient();
        wc.withBasicApiToken("charlotte");
        makeRequestAndAssertLogin(wc, "charlotte");
    }

    private void makeRequestAndAssertLogin(JenkinsRule.WebClient wc, String expectedLogin) throws IOException, SAXException {
        WebRequest req = new WebRequest(new URL(j.getURL(),"whoAmI/api/json"));
        Page p = wc.getPage(req);
        String pageContent = p.getWebResponse().getContentAsString();
        String loginReceived = (String) JSONObject.fromObject(pageContent).get("name");
        assertEquals(expectedLogin, loginReceived.trim());
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

    @TestExtension("testTokenHelperMethods")
    public static class AuthRetrieval implements UnprotectedRootAction {
        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "test";
        }

        public HttpResponse doIndex() {
            User u = User.current();
            return HttpResponses.plainText(u!=null ? u.getId() : "anonymous");
        }
    }
}

package hudson.security.pages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * The signup page for {@link hudson.security.HudsonPrivateSecurityRealm}
 */
public class SignupPage {

    public HtmlForm signupForm;
    public final HtmlPage signupPage;

    public SignupPage(HtmlPage signupPage) {
        this.signupPage = signupPage;

        assertNotNull("The sign up page has a username field.", this.signupPage.getElementById("username"));
        for (HtmlForm signupForm : this.signupPage.getForms()) {
            if (signupForm.getInputsByName("username").size() == 0)
                continue;
            this.signupForm = signupForm;
        }

    }



    public void enterUsername(String username) {
        signupForm.getInputByName("username").setValue(username);
    }

    /**
     * Enters the password in password1 and password2.
     * You can then call {@link #enterPassword2(String)} if you want them to be different.
     */
    public void enterPassword(String password) {
        signupForm.getInputByName("password1").setValue(password);
        signupForm.getInputByName("password2").setValue(password);
    }

    public void enterPassword2(String password2) {
        signupForm.getInputByName("password2").setValue(password2);
    }

    public void enterFullName(String fullName) {
        signupForm.getInputByName("fullname").setValue(fullName);
    }

    public void enterEmail(String email) {
        signupForm.getInputByName("email").setValue(email);
    }

    public HtmlPage submit(JenkinsRule rule) throws Exception {
        return rule.submit(signupForm);
    }

    public void assertErrorContains(String msg) {
        assertThat(signupForm.getPage().getElementById("main-panel").getTextContent(),containsString(msg));
    }
}

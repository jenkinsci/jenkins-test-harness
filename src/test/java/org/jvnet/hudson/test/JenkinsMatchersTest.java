package org.jvnet.hudson.test;

import hudson.util.Secret;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.JenkinsMatchers.hasPlainText;
import static org.jvnet.hudson.test.JenkinsMatchers.matchesPattern;

public class JenkinsMatchersTest {

    @Test
    public void testHasPlainText() {
        String plaintext = UUID.randomUUID().toString();
        Secret secret = Secret.fromString(plaintext);
        assertThat(secret, hasPlainText(not(isEmptyOrNullString())));
        assertThat(secret, hasPlainText(matchesPattern("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")));
    }

    @Test
    public void testMatchesPattern() {
        int value = ThreadLocalRandom.current().nextInt(42);
        assertThat(String.valueOf(value), matchesPattern("\\d+"));
    }
}
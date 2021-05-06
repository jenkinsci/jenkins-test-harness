package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 meta annotation providing {@link org.jvnet.hudson.test.JenkinsRule} integration.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(JenkinsRuleResolver.class)
public @interface JenkinsRule {
}

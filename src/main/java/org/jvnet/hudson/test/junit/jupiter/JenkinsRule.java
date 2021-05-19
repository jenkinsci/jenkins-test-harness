package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 meta annotation providing {@link org.jvnet.hudson.test.JenkinsRule JenkinsRule} integration.
 *
 * <p>
 * Test methods using the rule extension need to accept it by {@link JenkinsRuleExtension} parameter; each test case
 * gets a new rule object.
 * <p>
 * Annotating a <i>class</i> provides access for all of it's tests. Unrelated test cases can omit the parameter.
 *
 * <pre>
 * {@code
 * @JenkinsRule
 * class ExampleJUnit5Test {
 *
 *     @Test
 *     public void example(JenkinsRuleExtension r) {
 *         // use 'r' ...
 *     }
 *
 *     @Test
 *     public void exampleNotUsingRule() {
 *         // ...
 *     }
 * }
 * }
 * </pre>
 * <p>
 * Annotating a <i>method</i> limits access to the method.
 *
 * <pre>
 * {@code
 * class ExampleJUnit5Test {
 *
 *     @JenkinsRule
 *     @Test
 *     public void example(JenkinsRuleExtension r) {
 *         // use 'r' ...
 *     }
 * }
 * }
 * </pre>
 *
 * @see org.jvnet.hudson.test.junit.jupiter.JenkinsRuleResolver
 * @see org.junit.jupiter.api.extension.ExtendWith
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(JenkinsRuleResolver.class)
public @interface JenkinsRule {
}

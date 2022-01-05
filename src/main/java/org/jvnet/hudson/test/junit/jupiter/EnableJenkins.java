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
 * Test methods using the rule extension need to accept it by {@link Junit5JenkinsRule} parameter; each test case
 * gets a new rule object.
 * <p>
 * Annotating a <em>class</em> provides access for all of its tests. Unrelated test cases can omit the parameter.
 *
 * <blockquote><pre>
 * &#64;JenkinsRule
 * class ExampleJUnit5Test {
 *
 *     &#64;Test
 *     public void example(JenkinsRuleExtension r) {
 *         // use 'r' ...
 *     }
 *
 *     &#64;Test
 *     public void exampleNotUsingRule() {
 *         // ...
 *     }
 * }
 * </pre></blockquote>
 * <p>
 * Annotating a <i>method</i> limits access to the method.
 *
 * <blockquote><pre>
 * class ExampleJUnit5Test {
 *
 *     &#64;JenkinsRule
 *     &#64;Test
 *     public void example(JenkinsRuleExtension r) {
 *         // use 'r' ...
 *     }
 * }
 * </pre></blockquote>
 *
 * @see JenkinsExtension
 * @see org.junit.jupiter.api.extension.ExtendWith
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(JenkinsExtension.class)
public @interface EnableJenkins {
}

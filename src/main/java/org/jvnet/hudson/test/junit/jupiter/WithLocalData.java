package org.jvnet.hudson.test.junit.jupiter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a test method so that JenkinsExtension will use preset {@code JENKINS_HOME}
 * data loaded from either the test method or from the test class.
 *
 * <p>
 * For example, if the test method is {@code org.acme.FooTest.testBar()}, then
 * you can have your test data in one of the following places in resources folder
 * (typically {@code src/test/resources}):
 *
 * <ol>
 * <li>
 * Under {@code org/acme/FooTest/testBar} directory; that is, you could have files such as
 * {@code org/acme/FooTest/testBar/config.xml} or {@code org/acme/FooTest/testBar/jobs/p/config.xml},
 * in the same layout as in the real {@code JENKINS_HOME} directory.
 * <li>
 * In {@code org/acme/FooTest/testBar.zip} as a zip file.
 * <li>
 * Under {@code org/acme/FooTest} directory; that is, you could have files such as
 * {@code org/acme/FooTest/config.xml} or {@code org/acme/FooTest/jobs/p/config.xml},
 * in the same layout as in the real {@code JENKINS_HOME} directory.
 * <li>
 * In {@code org/acme/FooTest.zip} as a zip file.
 * </ol>
 *
 * You can specify a value to use instead of the method name
 * with the parameter of annotation. Should be a valid java identifier.
 * E.g. {@code @LocalData("commonData")} results using
 * {@code org/acme/FooTest/commonData(.zip)}.
 *
 * <p>
 * Search is performed in this specific order. The fall-back mechanism allows you to write
 * one test class that interacts with different aspects of the same data set, by associating
 * the dataset with a test class, or have a data set local to a specific test method.
 *
 * <p>
 * The choice of zip and directory depends on the nature of the test data, as well as the size of it.
 *
 * @author Alex Earl
 * @see JenkinsExtension
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithLocalData {
    String value() default "";
}

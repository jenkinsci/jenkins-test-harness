package org.jenkinsci.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class JenkinsBootTestFilterTest {

    private static final String PROPERTY = "jenkins.tests.excludeJenkins";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void includesEverythingWhenFilterDisabled() throws Exception {
        System.clearProperty(PROPERTY);
        FilterResult result = new JenkinsBootTestFilter()
                .apply(methodDescriptor(WithJenkinsSamples.class, "withRule", JenkinsRule.class));
        assertTrue(result.included());
    }

    @Test
    void excludesMethodAnnotatedDirectlyWithRuleParameter() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result = new JenkinsBootTestFilter()
                .apply(methodDescriptor(WithJenkinsSamples.class, "withRule", JenkinsRule.class));
        assertFalse(result.included());
    }

    @Test
    void includesAnnotatedMethodWithoutRuleParameter() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result =
                new JenkinsBootTestFilter().apply(methodDescriptor(WithJenkinsSamples.class, "withoutRule"));
        assertTrue(result.included());
    }

    @Test
    void excludesClassLevelAnnotatedMethodWithRuleParameter() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result = new JenkinsBootTestFilter()
                .apply(methodDescriptor(ClassLevelSample.class, "withRule", JenkinsRule.class));
        assertFalse(result.included());
    }

    @Test
    void includesClassLevelAnnotatedMethodWithoutRuleParameter() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result =
                new JenkinsBootTestFilter().apply(methodDescriptor(ClassLevelSample.class, "withoutRule"));
        assertTrue(result.included());
    }

    @Test
    void resolvesOverloadedMethodByItsOwnParameterTypes() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult withParam = new JenkinsBootTestFilter()
                .apply(methodDescriptor(WithJenkinsSamples.class, "overloaded", JenkinsRule.class));
        FilterResult withoutParam =
                new JenkinsBootTestFilter().apply(methodDescriptor(WithJenkinsSamples.class, "overloaded"));

        assertFalse(withParam.included());
        assertTrue(withoutParam.included());
    }

    @Test
    void detectsWithJenkinsEvenWhenOptionalCasCAnnotationIsAbsentFromClasspath() throws Exception {
        // io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode is not a
        // dependency of this module, so resolving it throws ClassNotFoundException; that must not
        // prevent detection of the always-available @WithJenkins annotation.
        System.setProperty(PROPERTY, "true");
        FilterResult result = new JenkinsBootTestFilter()
                .apply(methodDescriptor(WithJenkinsSamples.class, "withRule", JenkinsRule.class));
        assertFalse(result.included());
    }

    @Test
    void includesUnannotatedClass() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result = new JenkinsBootTestFilter().apply(classDescriptor(JenkinsBootTestFilterTest.class));
        assertTrue(result.included());
    }

    @Test
    void excludesAnnotatedClassSource() throws Exception {
        System.setProperty(PROPERTY, "true");
        FilterResult result = new JenkinsBootTestFilter().apply(classDescriptor(ClassLevelSample.class));
        assertFalse(result.included());
    }

    @Test
    void isRegisteredAsPostDiscoveryFilterViaServiceLoader() {
        boolean found = false;
        for (PostDiscoveryFilter filter : ServiceLoader.load(PostDiscoveryFilter.class)) {
            if (filter instanceof JenkinsBootTestFilter) {
                found = true;
            }
        }
        assertTrue(found, "JenkinsBootTestFilter must be discoverable via META-INF/services");
    }

    private static TestDescriptor methodDescriptor(Class<?> testClass, String methodName, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Method method = testClass.getDeclaredMethod(methodName, paramTypes);
        return sourcedDescriptor(MethodSource.from(testClass, method));
    }

    private static TestDescriptor classDescriptor(Class<?> testClass) {
        return sourcedDescriptor(ClassSource.from(testClass));
    }

    private static TestDescriptor sourcedDescriptor(TestSource source) {
        TestDescriptor descriptor = mock(TestDescriptor.class);
        when(descriptor.getSource()).thenReturn(Optional.of(source));
        return descriptor;
    }

    static class WithJenkinsSamples {
        @WithJenkins
        void withRule(JenkinsRule r) {}

        @WithJenkins
        void withoutRule() {}

        @WithJenkins
        void overloaded() {}

        @WithJenkins
        void overloaded(JenkinsRule r) {}
    }

    @WithJenkins
    static class ClassLevelSample {
        void withRule(JenkinsRule r) {}

        void withoutRule() {}
    }
}

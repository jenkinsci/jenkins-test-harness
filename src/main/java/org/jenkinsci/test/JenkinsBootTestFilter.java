package org.jenkinsci.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * PostDiscoveryFilter that excludes tests (when enabled) which boot a real Jenkins instance via
 * {@code org.jvnet.hudson.test.junit.jupiter.WithJenkins} or
 * {@code io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode}.
 *
 * Enabled by setting -Djenkins.tests.excludeJenkins=true (opt-in). By default the filter is a no-op.
 *
 * Both annotations only boot Jenkins for a method that declares the corresponding rule parameter
 * ({@link JenkinsRule} / {@code JenkinsConfiguredWithCodeRule}); per their own javadoc, an
 * annotated method without that parameter behaves as if it were not annotated, so the filter
 * mirrors that check instead of excluding every method in an annotated class.
 */
public class JenkinsBootTestFilter implements PostDiscoveryFilter {
    private static final String WITH_JENKINS = "org.jvnet.hudson.test.junit.jupiter.WithJenkins";
    private static final String WITH_JENKINS_CONFIGURED =
            "io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode";
    private static final String JENKINS_CONFIGURED_WITH_CODE_RULE =
            "io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule";

    private static final String EXCLUDE_PROPERTY = "jenkins.tests.excludeJenkins";

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (!Boolean.getBoolean(EXCLUDE_PROPERTY)) {
            return FilterResult.included("filter disabled");
        }
        Optional<TestSource> sourceOpt = descriptor.getSource();
        if (!sourceOpt.isPresent()) {
            return FilterResult.included("no source");
        }
        TestSource source = sourceOpt.get();

        try {
            if (source instanceof MethodSource) {
                MethodSource ms = (MethodSource) source;
                if (bootsJenkins(ms.getJavaClass(), ms.getJavaMethod())) {
                    return FilterResult.excluded(
                            "method boots Jenkins via WithJenkins or WithJenkinsConfiguredWithCode");
                }
            } else if (source instanceof ClassSource) {
                Class<?> cls = ((ClassSource) source).getJavaClass();
                if (isAnnotated(cls, WITH_JENKINS) || isAnnotated(cls, WITH_JENKINS_CONFIGURED)) {
                    return FilterResult.excluded("class annotated with WithJenkins or WithJenkinsConfiguredWithCode");
                }
            } else {
                return FilterResult.included("unsupported source");
            }
        } catch (Throwable t) {
            // If any reflection error occurs, include the test rather than risk excluding wrongly
            return FilterResult.included("error while checking annotations: " + t.getMessage());
        }

        return FilterResult.included("no Jenkins annotations detected");
    }

    private static boolean bootsJenkins(Class<?> cls, Method method) {
        boolean withJenkins = isAnnotated(cls, WITH_JENKINS) || isAnnotated(method, WITH_JENKINS);
        if (withJenkins && hasParameterOfType(method, JenkinsRule.class.getName())) {
            return true;
        }
        boolean withJenkinsConfigured =
                isAnnotated(cls, WITH_JENKINS_CONFIGURED) || isAnnotated(method, WITH_JENKINS_CONFIGURED);
        return withJenkinsConfigured && hasParameterOfType(method, JENKINS_CONFIGURED_WITH_CODE_RULE);
    }

    private static boolean hasParameterOfType(Method method, String parameterTypeName) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType().getName().equals(parameterTypeName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean isAnnotated(AnnotatedElement element, String annotationClassName) {
        try {
            Class<? extends Annotation> annotationClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            if (AnnotationSupport.isAnnotated(element, annotationClass)) {
                return true;
            }
            if (element instanceof Class) {
                Class<?> sup = ((Class<?>) element).getSuperclass();
                while (sup != null && sup != Object.class) {
                    if (AnnotationSupport.isAnnotated(sup, annotationClass)) {
                        return true;
                    }
                    sup = sup.getSuperclass();
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            // Annotation type not present on classpath (e.g. the optional Configuration as Code dependency)
            return false;
        }
    }
}

package org.jenkinsci.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * PostDiscoveryFilter that excludes tests (when enabled) which are annotated with
 * org.jvnet.hudson.test.WithJenkins or org.jvnet.hudson.test.WithJenkinsConfiguredWithCode.
 *
 * Enabled by setting -Djenkins.tests.excludeJenkins=true (opt-in). By default the filter is a no-op.
 */
public class JenkinsBootTestFilter implements PostDiscoveryFilter {
    private static final String WITH_JENKINS = "org.jvnet.hudson.test.WithJenkins";
    private static final String WITH_JENKINS_CONFIGURED = "org.jvnet.hudson.test.WithJenkinsConfiguredWithCode";

    private static final boolean EXCLUDE = Boolean.getBoolean("jenkins.tests.excludeJenkins");

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (!EXCLUDE) {
            return FilterResult.included("filter disabled");
        }
        Optional<TestSource> sourceOpt = descriptor.getSource();
        if (!sourceOpt.isPresent()) {
            return FilterResult.included("no source");
        }
        TestSource source = sourceOpt.get();
        String className = null;
        String methodName = null;
        if (source instanceof MethodSource) {
            MethodSource ms = (MethodSource) source;
            className = ms.getClassName();
            methodName = ms.getMethodName();
        } else if (source instanceof ClassSource) {
            ClassSource cs = (ClassSource) source;
            className = cs.getClassName();
        }

        if (className == null) {
            return FilterResult.included("unknown source");
        }

        try {
            Class<?> cls = Class.forName(className);
            if (hasJenkinsAnnotationOnClass(cls)) {
                return FilterResult.excluded("class annotated with WithJenkins or WithJenkinsConfiguredWithCode");
            }
            if (methodName != null) {
                Method m = findMethod(cls, methodName);
                if (m != null && hasJenkinsAnnotationOnMethod(m)) {
                    return FilterResult.excluded("method annotated with WithJenkins or WithJenkinsConfiguredWithCode");
                }
            }
        } catch (Throwable t) {
            // If any reflection error occurs, include the test rather than risk excluding wrongly
            return FilterResult.included("error while checking annotations: " + t.getMessage());
        }

        return FilterResult.included("no Jenkins annotations detected");
    }

    @SuppressWarnings("unchecked")
    private static boolean hasJenkinsAnnotationOnClass(Class<?> cls) {
        try {
            Class<? extends Annotation> a1 = (Class<? extends Annotation>) Class.forName(WITH_JENKINS);
            Class<? extends Annotation> a2 = (Class<? extends Annotation>) Class.forName(WITH_JENKINS_CONFIGURED);
            if (AnnotationSupport.isAnnotated(cls, a1) || AnnotationSupport.isAnnotated(cls, a2)) {
                return true;
            }
            Class<?> sup = cls.getSuperclass();
            while (sup != null && sup != Object.class) {
                if (AnnotationSupport.isAnnotated(sup, a1) || AnnotationSupport.isAnnotated(sup, a2)) {
                    return true;
                }
                sup = sup.getSuperclass();
            }
        } catch (ClassNotFoundException e) {
            // Annotation types not present on classpath; treat as not annotated
            return false;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasJenkinsAnnotationOnMethod(Method m) {
        try {
            Class<? extends Annotation> a1 = (Class<? extends Annotation>) Class.forName(WITH_JENKINS);
            Class<? extends Annotation> a2 = (Class<? extends Annotation>) Class.forName(WITH_JENKINS_CONFIGURED);
            return AnnotationSupport.isAnnotated(m, a1) || AnnotationSupport.isAnnotated(m, a2);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Method findMethod(Class<?> cls, String methodName) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }
}

package org.jvnet.hudson.test.junit.jupiter;

import hudson.Functions;
import org.junit.jupiter.api.extension.*;
import org.junit.rules.Timeout;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;
import org.jvnet.hudson.test.HudsonHomeLoader.Local;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.io.Serial;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.sql.Time;
import java.util.concurrent.*;
import java.util.logging.Logger;


/**
 * JUnit 5 extension providing {@link JenkinsRule} integration.
 *
 * @see WithJenkins
 */
class JenkinsExtension implements ParameterResolver, InvocationInterceptor, AfterEachCallback {

    private static final Logger LOGGER = Logger.getLogger(JenkinsExtension.class.getName());

    private static final String KEY = "jenkins-instance";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(JenkinsExtension.class);

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        final JenkinsRule rule = context.getStore(NAMESPACE).remove(KEY, JenkinsRule.class);
        if (rule == null) {
            return;
        }

        try {
            executeWithTimeout(() -> {
                rule.after();
                return null;
            }, context, rule);
        } catch (InvocationExceptionWrapper wrapped) {
            if (wrapped.getCause() instanceof Exception exception) {
                throw exception;
            } else {
                throw new RuntimeException(wrapped.getCause());
            }
        } catch (Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        final JenkinsRule rule = extensionContext.getStore(NAMESPACE).get(KEY, JenkinsRule.class);
        if (rule == null) {
            return;
        }

        try {
            executeWithTimeout(() -> {
                try {
                    return invocation.proceed();
                } catch (Throwable t) {
                    throw new InvocationExceptionWrapper(t);
                }
            }, extensionContext, rule);
        } catch (Throwable t) {
            if (t instanceof InvocationExceptionWrapper wrapped) {
                throw wrapped.getCause();
            } else {
                throw t;
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(JenkinsRule.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        final JenkinsRule rule =
                extensionContext
                        .getStore(NAMESPACE)
                        .getOrComputeIfAbsent(
                                KEY,
                                key -> new JUnit5JenkinsRule(parameterContext, extensionContext),
                                JenkinsRule.class);

        if (extensionContext.getTestMethod().isPresent()) {
            // check for a WithLocalData annotation to set up JENKINS_HOME
            Method testMethod = extensionContext.getTestMethod().get();
            WithLocalData localData = testMethod.getAnnotation(WithLocalData.class);
            if (localData == null && extensionContext.getTestClass().isPresent()) {
                Class<?> testClass = extensionContext.getTestClass().get();
                localData = testClass.getAnnotation(WithLocalData.class);
            }

            if (localData != null) {
                rule.with(new Local(testMethod, localData.value()));
            }
        }

        try {
            executeWithTimeout(() -> {
                try {
                    rule.before();
                    return null;
                } catch (Throwable t) {
                    throw new ParameterResolutionException(t.getMessage(), t);
                }
            }, extensionContext, rule);
            return rule;
        } catch (ParameterResolutionException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new ParameterResolutionException(t.getMessage(), t);
        }
    }

    private static void executeWithTimeout(Callable<Void> callable, ExtensionContext extensionContext, JenkinsRule rule) throws Throwable {
        Method testMethod = extensionContext.getTestMethod().get();
        WithTimeout withTimeout = testMethod.getAnnotation(WithTimeout.class);
        final int testTimeout = withTimeout != null ? withTimeout.value(): rule.timeout;

        if (testTimeout > 0) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> {
                    try {
                        callable.call();
                    } catch (Exception ex) {
                        throw new InvocationExceptionWrapper(ex);
                    }
                }).get(testTimeout, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                // retrieve the original cause
                if (ex.getCause() instanceof InvocationExceptionWrapper wrapped) {
                    throw wrapped.getCause();
                } else {
                    throw ex.getCause();
                }
            } catch (TimeoutException ex) {
                ThreadInfo[] threadInfos = Functions.getThreadInfos();
                Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
                for (ThreadInfo ti : threadInfos) {
                    LOGGER.warning(Functions.dumpThreadInfo(ti, m));
                }
                throw new TimeoutException(String.format("%s() timed out after %d seconds", testMethod.getName(), testTimeout));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static class InvocationExceptionWrapper extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public InvocationExceptionWrapper(Throwable cause) {
            super(cause);
        }
    }
}

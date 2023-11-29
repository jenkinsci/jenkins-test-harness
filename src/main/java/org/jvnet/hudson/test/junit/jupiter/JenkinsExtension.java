package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.platform.commons.support.ReflectionSupport.findFields;

/**
 * JUnit 5 extension providing {@link JenkinsRule} integration.
 *
 * @see WithJenkins
 */
class JenkinsExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver, AfterEachCallback {

    private static final String KEY = "jenkins-instance";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(JenkinsExtension.class);

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        Class<?> clazz = extensionContext.getRequiredTestClass();
        Predicate<Field> predicate = (field -> ModifierSupport.isStatic(field)
            && JenkinsRule.class.isAssignableFrom(field.getType()));
        Field field = findFields(clazz, predicate, HierarchyTraversalMode.BOTTOM_UP).stream()
            .findFirst()
            .orElse(null);
        if (field == null) {
            return;
        }

        final JenkinsRecipe recipe = field.getDeclaredAnnotation(JenkinsRecipe.class);
        final JenkinsRule rule;
        if (recipe != null) {
            rule = new JUnit5JenkinsRule(extensionContext, recipe);
        } else {
            rule = new JUnit5JenkinsRule(extensionContext);
        }
        extensionContext
            .getStore(NAMESPACE)
            .getOrComputeIfAbsent(KEY, key -> rule, JenkinsRule.class);
        try {
            rule.before();
        } catch (Throwable e) {
            throw new ExtensionContextException(e.getMessage(), e);
        }
        field.setAccessible(true);
        field.set(null, rule);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        Class<?> clazz = extensionContext.getRequiredTestClass();
        Predicate<Field> predicate = (field -> ModifierSupport.isStatic(field)
            && JenkinsRule.class.isAssignableFrom(field.getType()));
        Field field = findFields(clazz, predicate, HierarchyTraversalMode.BOTTOM_UP).stream()
            .findFirst()
            .orElse(null);
        if (field != null) {
            final JenkinsRule rule =
                extensionContext.getStore(NAMESPACE).get(KEY, JenkinsRule.class);
            if (rule == null) {
                return;
            }
            rule.after();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        final JenkinsRule rule = context.getStore(NAMESPACE).remove(KEY, JenkinsRule.class);
        if (rule == null) {
            return;
        }
        rule.after();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(JenkinsRule.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        JenkinsRecipe recipe = parameterContext.findAnnotation(JenkinsRecipe.class).orElse(null);
        Function<String, JenkinsRule> compute;
        if (recipe == null) {
            compute = key -> new JUnit5JenkinsRule(extensionContext);
        } else {
            compute = key -> new JUnit5JenkinsRule(extensionContext, recipe);
        }

        final JenkinsRule rule =
                extensionContext
                        .getStore(NAMESPACE)
                        .getOrComputeIfAbsent(KEY, compute, JenkinsRule.class);

        try {
            rule.before();
            return rule;
        } catch (Throwable t) {
            throw new ParameterResolutionException(t.getMessage(), t);
        }
    }
}

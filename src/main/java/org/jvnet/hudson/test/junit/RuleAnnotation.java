/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.test.junit;

import org.junit.rules.TestRule;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Meta-annotation for annotations that introduces a {@link TestRule} for test.
 *
 * <p>
 * This allows annotations on test class/method to add additional setup/shutdown behaviours.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
@Documented
public @interface RuleAnnotation {
    /**
     * The rule class that defines the setup/shutdown behaviour.
     *
     * The instance is obtained through Guice.
     */
    Class<? extends TestRule> value();

    /**
     * Optional ordering among rules.
     *
     * Annotation with <tt>priority >= 0</tt> are guaranteed to be run after
     * Jenkins is up. Negative priorities are run before startup on best effort
     * basis. (It might not happen before for ExistingJenkinsController,
     * PooledJenkinsController and possibly others).
     *
     * Annotations that skips execution are encouraged to run before Jenkins is
     * booted up to save time. Note, that these implementations can not inject
     * Jenkins for obvious reasons.
     */
    int priority() default 0;
}

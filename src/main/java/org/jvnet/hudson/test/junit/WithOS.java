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

import org.apache.commons.lang.SystemUtils;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * Indicates that the test must be running on one of the operating systems
 * provided. If this condition is not met, this test will be skipped.
 * 
 **/
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Inherited
@Documented
@RuleAnnotation(value = WithOS.RuleImpl.class, priority = -10)
public @interface WithOS {

    public enum OS {
        WINDOWS,
        LINUX,
        MAC
    }

    OS[] os();

    public class RuleImpl implements TestRule {
        @Override
        public Statement apply(final Statement base, final Description d) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    Class<?> testSuite = d.getTestClass();
                    check(d.getAnnotation(WithOS.class), testSuite);
                    check(testSuite.getAnnotation(WithOS.class), testSuite);

                    base.evaluate();
                }

                private void check(WithOS withos, Class<?> testCase) {
                    if (withos == null) return;

                    String errorMsg = "Test must be running on any of the following operating systems: " + Arrays.toString(withos.os());
                    for (OS _os : withos.os()) {
                        switch (_os) {
                            case LINUX:
                                if (!SystemUtils.IS_OS_LINUX) {
                                    throw new AssumptionViolatedException(errorMsg);
                                }
                                break;
                            case WINDOWS:
                                if (!SystemUtils.IS_OS_WINDOWS) {
                                    throw new AssumptionViolatedException(errorMsg);
                                }
                                break;
                            case MAC:
                                if (!SystemUtils.IS_OS_MAC) {
                                    throw new AssumptionViolatedException(errorMsg);
                                }
                                break;
                            default:
                                break;
                        }
                    }

                }
            };
        }
    }
}

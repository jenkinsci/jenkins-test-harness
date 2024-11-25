/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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
package jenkins.test;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import hudson.model.Run;
import java.io.IOException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Matchers for {@link Run} objects.
 */
public final class RunMatchers {
    private RunMatchers() {}

    /**
     * Creates a matcher checking whether a build is successful.
     */
    public static Matcher<Run<?,?>> isSuccessful() {
        return new RunResultMatcher(Result.SUCCESS);
    }

    /**
     * Creates a matcher checking whether a build has a specific outcome.
     */
    public static Matcher<Run<?,?>> hasStatus(Result result) {
        return new RunResultMatcher(result);
    }

    /**
     * Creates a matcher checking whether build logs contain a specific message.
     * @param message the expected message
     */
    public static Matcher<Run<?,?>> logContains(String message) {
        return new RunLogMatcher(message);
    }

    private static class RunResultMatcher extends TypeSafeMatcher<Run<?,?>> {
        @NonNull
        private final Result expectedResult;

        public RunResultMatcher(@NonNull Result expectedResult) {
            this.expectedResult = expectedResult;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a build with result " + expectedResult);
        }

        @Override
        protected boolean matchesSafely(Run run) {
            return run.getResult() == expectedResult;
        }

        @Override
        protected void describeMismatchSafely(Run<?, ?> item, Description mismatchDescription) {
            mismatchDescription.appendText("was ").appendValue(item.getResult());
        }
    }

    private static class RunLogMatcher extends TypeSafeMatcher<Run<?, ?>> {
        @NonNull
        private final String message;

        private RunLogMatcher(@NonNull String message) {
            this.message = message;
        }

        @Override
        protected boolean matchesSafely(Run<?, ?> run) {
            try {
                return JenkinsRule.getLog(run).contains(message);
            } catch (IOException x) {
                return false;
            }
        }

        @Override
        protected void describeMismatchSafely(Run<?, ?> item, Description mismatchDescription) {
            mismatchDescription.appendText("was \n");
            try {
                mismatchDescription.appendText(JenkinsRule.getLog(item));
            } catch (IOException e) {
                mismatchDescription.appendText("<unreadable>");
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("log containing ").appendValue(message);
        }
    }
}

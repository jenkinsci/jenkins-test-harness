/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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
package org.jvnet.hudson.test;

import static org.hamcrest.MatcherAssert.assertThat;

import hudson.model.listeners.ItemListener;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link TestExtension}
 */
public class TestExtensionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static class AllTests extends ItemListener {
    }

    @TestExtension("test1")
    public static class SingleTests extends ItemListener {
    }

    @TestExtension({"test1", "test2"})
    public static class MultipleTests extends ItemListener {
    }

    private List<Class<? extends ItemListener>> getExtensionClasses() {
        return j.jenkins.getExtensionList(ItemListener.class).stream().map(ItemListener::getClass).collect(Collectors.toList());
    }

    @Test
    public void test1() {
        assertThat(
            getExtensionClasses(),
            Matchers.hasItems(AllTests.class, SingleTests.class, MultipleTests.class)
        );
    }

    @Test
    public void test2() {
        assertThat(
            getExtensionClasses(),
            Matchers.hasItems(AllTests.class, MultipleTests.class)
        );
        assertThat(
            getExtensionClasses(),
            Matchers.not(Matchers.hasItem(SingleTests.class))
        );
    }

    @Test
    public void test3() {
        assertThat(
            getExtensionClasses(),
            Matchers.hasItems(AllTests.class)
        );
        assertThat(
            getExtensionClasses(),
            Matchers.not(Matchers.hasItem(SingleTests.class))
        );
        assertThat(
            getExtensionClasses(),
            Matchers.not(Matchers.hasItem(MultipleTests.class))
        );
    }
}

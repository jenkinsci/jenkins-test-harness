/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.jvnet.hudson.test.fixtures;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Saves and restores sort of a flag, such as a {@code static} field or system property.
 * Usage: <pre>{@code
 * @RegisterExtension
 * private static final FlagExtension<String> FLAG_EXTENSION = new FlagExtension<>(() -> FLAG, x -> FLAG = x, true);
 *
 * public void method() {
 *     try {
 *         FIXTURE.setUp();
 *         […]
 *     } finally {
 *         FIXTURE.tearDown();
 *     }
 * }
 * }</pre>
 * @see org.jvnet.hudson.test.junit.jupiter.FlagExtension
 * @see org.jvnet.hudson.test.FlagRule
 */
public class FlagFixture<T> {

    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final boolean replace;
    private final T replacement;
    private T orig;

    public FlagFixture(Supplier<T> getter, Consumer<T> setter) {
        this.getter = getter;
        this.setter = setter;
        replace = false;
        replacement = null;
    }

    public FlagFixture(Supplier<T> getter, Consumer<T> setter, T replacement) {
        this.getter = getter;
        this.setter = setter;
        replace = true;
        this.replacement = replacement;
    }

    public void setUp() {
        orig = getter.get();
        if (replace) {
            setter.accept(replacement);
        }
    }

    public void tearDown() {
        setter.accept(orig);
    }

    public static FlagFixture<String> systemProperty(String key) {
        return new FlagFixture<>(() -> System.getProperty(key), value -> setProperty(key, value));
    }

    public static FlagFixture<String> systemProperty(String key, String replacement) {
        return new FlagFixture<>(() -> System.getProperty(key), value -> setProperty(key, value), replacement);
    }

    private static String setProperty(String key, String value) {
        if (value != null) {
            return System.setProperty(key, value);
        } else {
            return (String) System.getProperties().remove(key);
        }
    }
}

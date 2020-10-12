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

package org.jvnet.hudson.test;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

/**
 * Saves and restores sort of a flag, such as a {@code static} field or system property.
 */
public final class FlagRule<T> extends ExternalResource {

    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final boolean replace;
    private final T replacement;
    private T orig;

    public FlagRule(Supplier<T> getter, Consumer<T> setter) {
        this.getter = getter;
        this.setter = setter;
        replace = false;
        replacement = null;
    }

    public FlagRule(Supplier<T> getter, Consumer<T> setter, T replacement) {
        this.getter = getter;
        this.setter = setter;
        replace = true;
        this.replacement = replacement;
    }

    @Override
    protected void before() throws Throwable {
        orig = getter.get();
        if (replace) {
            setter.accept(replacement);
        }
    }

    @Override
    protected void after() {
        setter.accept(orig);
    }

    public static FlagRule<String> systemProperty(String key) {
        return new FlagRule<>(() -> System.getProperty(key), value -> setProperty(key, value));
    }

    public static FlagRule<String> systemProperty(String key, String replacement) {
        return new FlagRule<>(() -> System.getProperty(key), value -> setProperty(key, value), replacement);
    }

    private static String setProperty(String key, String value) {
        if (value != null) {
            return System.setProperty(key, value);
        } else {
            return (String) System.getProperties().remove(key);
        }
    }

}

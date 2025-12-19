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
import org.jvnet.hudson.test.fixtures.FlagFixture;

/**
 * This is the JUnit 4 implementation of {@link FlagFixture}.
 * Usage: <pre>{@code
 * @ClassRule
 * public static final FlagRule<String> FLAG_RULE = new FlagRule<>(() -> FLAG, x -> FLAG = x, true);
 * }</pre>
 *
 * @see FlagFixture
 */
public final class FlagRule<T> extends ExternalResource {

    private final FlagFixture<T> fixture;

    private FlagRule(FlagFixture<T> fixture) {
        this.fixture = fixture;
    }

    public FlagRule(Supplier<T> getter, Consumer<T> setter) {
        fixture = new FlagFixture<>(getter, setter);
    }

    public FlagRule(Supplier<T> getter, Consumer<T> setter, T replacement) {
        fixture = new FlagFixture<>(getter, setter, replacement);
    }

    @Override
    protected void before() throws Throwable {
        fixture.setUp();
    }

    @Override
    protected void after() {
        fixture.tearDown();
    }

    public static FlagRule<String> systemProperty(String key) {
        return new FlagRule<>(FlagFixture.systemProperty(key));
    }

    public static FlagRule<String> systemProperty(String key, String replacement) {
        return new FlagRule<>(FlagFixture.systemProperty(key, replacement));
    }
}

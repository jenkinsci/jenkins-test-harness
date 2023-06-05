/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;

public class PrefixedOutputStreamTest {

    @Rule public FlagRule<Boolean> skipCheckForCI = new FlagRule<>(() -> PrefixedOutputStream.Builder.SKIP_CHECK_FOR_CI, x -> PrefixedOutputStream.Builder.SKIP_CHECK_FOR_CI = x, true);

    @Test public void name() throws Exception {
        assertOutput(PrefixedOutputStream.builder().withName("xxx"),
            "[xxx] regular line\n[xxx] \n[xxx] split across\n[xxx] two lines\n[xxx] missing trailing newline");
    }

    @Test public void color() throws Exception {
        assertOutput(PrefixedOutputStream.builder().withColor(PrefixedOutputStream.Color.RED),
            "\u001b[31mregular line\u001b[0m\n\u001b[31m\u001b[0m\n\u001b[31msplit across\u001b[0m\n\u001b[31mtwo lines\u001b[0m\n\u001b[31mmissing trailing newline\u001b[0m");
    }

    @Test public void nameAndColor() throws Exception {
        assertOutput(PrefixedOutputStream.builder().withName("xxx").withColor(PrefixedOutputStream.Color.RED),
            "[xxx] \u001b[31mregular line\u001b[0m\n[xxx] \u001b[31m\u001b[0m\n[xxx] \u001b[31msplit across\u001b[0m\n[xxx] \u001b[31mtwo lines\u001b[0m\n[xxx] \u001b[31mmissing trailing newline\u001b[0m");
    }

    @Test public void neither() throws Exception {
        assertOutput(PrefixedOutputStream.builder(),
            "regular line\n\nsplit across\ntwo lines\nmissing trailing newline");
    }

    private static void assertOutput(PrefixedOutputStream.Builder prefixedOutputStreamBuilder, String expected) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(prefixedOutputStreamBuilder.build(baos))) {
            ps.println("regular line");
            ps.println(); // blank line
            ps.println("split across\ntwo lines");
            ps.print("missing trailing newline");
        }
        assertThat(baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            is(expected));
    }

}

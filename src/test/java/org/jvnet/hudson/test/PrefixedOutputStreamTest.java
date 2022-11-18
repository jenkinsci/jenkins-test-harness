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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class PrefixedOutputStreamTest {

    @Test public void name() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(PrefixedOutputStream.builder().withName("xxx").build(baos))) {
            ps.println("regular line");
            ps.println(); // blank line
            ps.println("split across\ntwo lines");
            ps.print("missing trailing newline");
        }
        assertThat(baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            is("[xxx] regular line\n[xxx] \n[xxx] split across\n[xxx] two lines\n[xxx] missing trailing newline"));
    }

    @Test public void color() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(PrefixedOutputStream.builder().withColor(PrefixedOutputStream.Color.RED).build(baos))) {
            ps.println("regular line");
            ps.println(); // blank line
            ps.println("split across\ntwo lines");
            ps.print("missing trailing newline");
        }
        assertThat(baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            is("\u001b[31mregular line\u001b[0m\n\u001b[31m\u001b[0m\n\u001b[31msplit across\u001b[0m\n\u001b[31mtwo lines\u001b[0m\n\u001b[31mmissing trailing newline\u001b[0m"));
    }

    @Test public void nameAndColor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(PrefixedOutputStream.builder().withName("xxx").withColor(PrefixedOutputStream.Color.RED).build(baos))) {
            ps.println("regular line");
            ps.println(); // blank line
            ps.println("split across\ntwo lines");
            ps.print("missing trailing newline");
        }
        assertThat(baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            is("[xxx] \u001b[31mregular line\u001b[0m\n[xxx] \u001b[31m\u001b[0m\n[xxx] \u001b[31msplit across\u001b[0m\n[xxx] \u001b[31mtwo lines\u001b[0m\n[xxx] \u001b[31mmissing trailing newline\u001b[0m"));
    }

    @Test public void neither() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(PrefixedOutputStream.builder().build(baos))) {
            ps.println("regular line");
            ps.println(); // blank line
            ps.println("split across\ntwo lines");
            ps.print("missing trailing newline");
        }
        assertThat(baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            is("regular line\n\nsplit across\ntwo lines\nmissing trailing newline"));
    }

}

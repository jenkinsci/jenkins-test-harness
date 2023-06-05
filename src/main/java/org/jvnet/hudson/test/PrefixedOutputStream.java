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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.LineTransformationOutputStream;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Decorating {@link OutputStream} which prefixes lines of text with an identifier and optional color.
 * (Does not include a timestamp, since typically something like {@link SupportLogFormatter} should already cover that.)
 */
public final class PrefixedOutputStream extends LineTransformationOutputStream.Delegating {

    public static Builder builder() {
        return new Builder();
    }

    private static class Color16 implements AnsiColor, Serializable  {
        private final Color color;
        private final boolean bold;

        Color16(Color color, boolean bold) {
            this.color = color;
            this.bold = bold;
        }

        @Override
        public String getCode() {
            return color.code + (bold ? ";1" : "");
        }
    }

    public enum Color implements AnsiColor {

        RED("31"), GREEN("32"), YELLOW("33"), BLUE("34"), MAGENTA("35"), CYAN("36");

        private final String code;

        Color(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public AnsiColor bold() {
            return new Color16(this, true);
        }
    }

    public interface AnsiColor {
        String getCode();
    }

    @CheckForNull private final String name;
    @CheckForNull private final AnsiColor color;

    private PrefixedOutputStream(OutputStream out, String name, AnsiColor color) {
        super(out);
        this.name = name;
        this.color = color;
    }

    @Override protected void eol(byte[] b, int len) throws IOException {
        if (name != null) {
            out.write('[');
            out.write(name.getBytes(StandardCharsets.US_ASCII));
            out.write(']');
            out.write(' ');
        }
        if (color != null) {
            out.write(27); // ESC
            out.write('[');
            out.write(color.getCode().getBytes(StandardCharsets.US_ASCII));
            out.write('m');
            // Preserving original line ending, so not using trimEOL:
            int preNlLen = len;
            while (preNlLen > 0 && (b[preNlLen - 1] == '\n' || b[preNlLen - 1] == '\r')) {
                preNlLen--;
            }
            assert 0 <= preNlLen;
            assert preNlLen <= len;
            assert len <= b.length;
            out.write(b, 0, preNlLen);
            out.write(27);
            out.write('[');
            out.write('0');
            out.write('m');
            out.write(b, preNlLen, len - preNlLen);
        } else {
            out.write(b, 0, len);
        }
    }

    public static final class Builder implements Serializable {

        static boolean SKIP_CHECK_FOR_CI;

        @CheckForNull private String name;
        @CheckForNull private AnsiColor color;

        private Builder() {}

        public Builder withName(@CheckForNull String name) {
            this.name = name;
            return this;
        }

        public @CheckForNull String getName() {
            return name;
        }

        public Builder withColor(@CheckForNull AnsiColor color) {
            if (SKIP_CHECK_FOR_CI || !"true".equals(System.getenv("CI"))) {
                this.color = color;
            }
            return this;
        }

        public @CheckForNull AnsiColor getColor() {
            return color;
        }

        public @NonNull OutputStream build(@NonNull OutputStream delegate) {
            return name != null || color != null ? new PrefixedOutputStream(delegate, name, color) : delegate;
        }

    }

}

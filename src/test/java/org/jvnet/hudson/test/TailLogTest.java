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

package org.jvnet.hudson.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class TailLogTest {

    @Rule public final TemporaryFolder tmp = new TemporaryFolder();

    @Test public void recreatedLog() throws Exception {
        var dir = tmp.getRoot();
        try (var tail = new TailLog(dir, "prj", 123).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            Thread.sleep(1000);
            var log = new File(dir, "log");
            try (var os = new FileOutputStream(log); var pw = new PrintWriter(os)) {
                for (int i = 0; i < 10; i++) {
                    pw.println(i);
                    pw.flush();
                    Thread.sleep(500);
                }
            }
            var log2 = new File(dir, "log.tmp");
            FileUtils.copyFile(log, log2);
            FileUtils.delete(log);
            assertTrue(log2.renameTo(log));
            try (var os = new FileOutputStream(log, true); var pw = new PrintWriter(os)) {
                for (int i = 10; i < 20; i++) {
                    pw.println(i);
                    pw.flush();
                    Thread.sleep(500);
                }
                pw.println("Finished: WHATEVER");
            }
            tail.waitForCompletion();
        }
    }

}

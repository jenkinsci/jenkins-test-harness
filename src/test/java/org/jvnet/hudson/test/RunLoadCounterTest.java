/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public final class RunLoadCounterTest {

    @Test
    void smokes(JenkinsRule r) throws Exception {
        var p = r.createFreeStyleProject();
        for (int i = 1; i <= 10; i++) {
            assertThat(r.buildAndAssertSuccess(p).number, is(i));
        }
        assertThat(
                RunLoadCounter.countLoads(p, () -> {
                    for (int i = 1; i <= 9; i += 2) {
                        assertThat(p.getBuildByNumber(i).number, is(i));
                    }
                }),
                is(5));
        Callable<Boolean> twoLoads = () -> {
            for (int i = 1; i <= 6; i += 5) {
                assertThat(p.getBuildByNumber(i).number, is(i));
            }
            return true;
        };
        assertThat(RunLoadCounter.assertMaxLoads(p, 2, twoLoads), is(true));
        assertThat(RunLoadCounter.assertMaxLoads(p, 3, twoLoads), is(true));
        assertThrows(AssertionError.class, () -> RunLoadCounter.assertMaxLoads(p, 1, twoLoads));
        for (int i = 11; i <= 20; i++) {
            assertThat(r.buildAndAssertSuccess(p).number, is(i));
        }
        assertThat(
                RunLoadCounter.countLoads(p, () -> {
                    for (int i = 1; i <= 19; i += 2) {
                        assertThat(p.getBuildByNumber(i).number, is(i));
                    }
                }),
                is(10));
        assertThat(RunLoadCounter.assertMaxLoads(p, 2, twoLoads), is(true));
        assertThat(RunLoadCounter.assertMaxLoads(p, 3, twoLoads), is(true));
        assertThrows(AssertionError.class, () -> RunLoadCounter.assertMaxLoads(p, 1, twoLoads));
    }
}

/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.swing.BoundedRangeModel;
import jenkins.model.Jenkins;
import org.netbeans.insane.impl.LiveEngine;
import org.netbeans.insane.live.LiveReferences;
import org.netbeans.insane.live.Path;
import org.netbeans.insane.scanner.CountingVisitor;
import org.netbeans.insane.scanner.Filter;
import org.netbeans.insane.scanner.ObjectMap;
import org.netbeans.insane.scanner.ScannerUtils;

/**
 * Static utility methods for verifying heap memory usage.
 * Uses the <a href="http://performance.netbeans.org/insane/">INSANE library</a>
 * to traverse the heap from within your test.
 * <p>Object sizes are in an idealized JVM in which pointers are 4 bytes
 * (realistic even for modern 64-bit JVMs in which {@code -XX:+UseCompressedOops} is the default)
 * but objects are aligned on 8-byte boundaries (so dropping an {@code int} field does not always save memory).
 * <p>{@code import static org.jvnet.hudson.test.MemoryAssert.*;} to use.
 */
public class MemoryAssert {

    private MemoryAssert() {}

    /**
     * Verifies that an object and its transitive reference graph occupy at most a predetermined amount of memory.
     * The referents of {@link WeakReference} and the like are ignored.
     * <p>To use, run your test for the first time with {@code max} of {@code 0};
     * when it fails, use the reported actual size as your assertion maximum.
     * When improving memory usage, run again with {@code 0} and tighten the test to both demonstrate
     * your improvement quantitatively and prevent regressions.
     * @param o the object to measure
     * @param max the maximum desired memory usage (in bytes)
     */
    public static void assertHeapUsage(Object o, int max) throws Exception {
        // TODO could use ScannerUtils.recursiveSizeOf here
        CountingVisitor v = new CountingVisitor();
        ScannerUtils.scan(ScannerUtils.skipNonStrongReferencesFilter(), v, Set.of(o), false);
        int memoryUsage = v.getTotalSize();
        assertTrue(o + " consumes " + memoryUsage + " bytes of heap, " + (memoryUsage - max) + " over the limit of " + max, memoryUsage <= max);
    }

    /**
     * @see #increasedMemory
     * @since 1.500
     */
    public static final class HistogramElement implements Comparable<HistogramElement> {
        public final String className;
        public final int instanceCount;
        public final int byteSize;
        HistogramElement(String className, int instanceCount, int byteSize) {
            this.className = className;
            this.instanceCount = instanceCount;
            this.byteSize = byteSize;
        }
        @Override public int compareTo(HistogramElement o) {
            int r = o.byteSize - byteSize;
            return r != 0 ? r : className.compareTo(o.className);
        }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof HistogramElement)) {
                return false;
            }
            HistogramElement o = (HistogramElement) obj;
            return o.className.equals(className);
        }
        @Override public int hashCode() {
            return className.hashCode();
        }
    }

    /**
     * Counts how much more memory is held in Jenkins by doing some operation.
     * @param callable an action
     * @param filters things to exclude
     * @return a histogram of the heap delta after running the operation
     * @since 1.500
     */
    public static List<HistogramElement> increasedMemory(Callable<Void> callable, Filter... filters) throws Exception {
        Filter f = ScannerUtils.skipNonStrongReferencesFilter();
        if (filters.length > 0) {
            Filter[] fs = new Filter[filters.length + 1];
            fs[0] = f;
            System.arraycopy(filters, 0, fs, 1, filters.length);
            f = ScannerUtils.compoundFilter(fs);
        }
        CountingVisitor v1 = new CountingVisitor();
        ScannerUtils.scan(f, v1, Set.of(Jenkins.get()), false);
        Set<Class<?>> old = v1.getClasses();
        callable.call();
        CountingVisitor v2 = new CountingVisitor();
        ScannerUtils.scan(f, v2, Set.of(Jenkins.get()), false);
        List<HistogramElement> elements = new ArrayList<>();
        for (Class<?> c : v2.getClasses()) {
            int delta = v2.getCountForClass(c) - (old.contains(c) ? v1.getCountForClass(c) : 0);
            if (delta > 0) {
                elements.add(new HistogramElement(c.getName(), delta, v2.getSizeForClass(c) - (old.contains(c) ? v1.getSizeForClass(c) : 0)));
            }
        }
        Collections.sort(elements);
        return elements;
    }

    @Deprecated
    public static void assertGC(WeakReference<?> reference) {
        assertGC(reference, true);
    }

    /**
     * Forces GC by causing an OOM and then verifies the given {@link WeakReference} has been garbage collected.
     * @param reference object used to verify garbage collection.
     * @param allowSoft if true, pass even if {@link SoftReference}s apparently needed to be cleared by forcing an {@link OutOfMemoryError};
     *                  if false, fail in such a case (though the failure will be slow)
     */
    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE_OF_NULL")
    public static void assertGC(WeakReference<?> reference, boolean allowSoft) {
        Runtime.Version runtimeVersion = Runtime.version();
        assumeTrue(
                "TODO JENKINS-67974 works on Java 17 but not 11",
                runtimeVersion.feature() >= 17);
        assertTrue(true); reference.get(); // preload any needed classes!
        System.err.println("Trying to collect " + reference.get() + "…");
        Set<Object[]> objects = new HashSet<>();
        int size = 1024;
        String softErr = null;
        while (reference.get() != null) {
            try {
                objects.add(new Object[size]);
                size *= 1.3;
            } catch (OutOfMemoryError ignore) {
                if (softErr != null) {
                    fail(softErr);
                } else {
                    break;
                }
            }
            System.gc();
            System.err.println("GC after allocation of size " + size);
            if (!allowSoft) {
                Object obj = reference.get();
                if (obj != null) {
                    softErr = "Apparent soft references to " + obj + ": " + fromRoots(Set.of(obj), null, null, new Filter() {
                        final Field referent;
                        {
                            try {
                                referent = Reference.class.getDeclaredField("referent");
                            } catch (NoSuchFieldException x) {
                                throw new AssertionError(x);
                            }
                        }
                        @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                            return !referent.equals(reference) || !(referredFrom instanceof WeakReference);
                        }
                    }) + "; apparent weak references: " + fromRoots(Set.of(obj), null, null, ScannerUtils.skipObjectsFilter(Set.of(reference), true));
                    System.err.println(softErr);
                }
            }
        }
        objects = null;
        System.gc();
        Object obj = reference.get();
        if (obj == null) {
            System.err.println("Successfully collected.");
        } else {
            System.err.println("Failed to collect " + obj + ", looking for strong references…");
            Map<Object,Path> rootRefs = fromRoots(Set.of(obj), null, null, ScannerUtils.skipNonStrongReferencesFilter());
            if (!rootRefs.isEmpty()) {
                fail(rootRefs.toString());
            } else {
                System.err.println("Did not find any strong references to " + obj + ", looking for soft references…");
                rootRefs = fromRoots(Set.of(obj), null, null, new Filter() {
                    final Field referent;
                    {
                        try {
                            referent = Reference.class.getDeclaredField("referent");
                        } catch (NoSuchFieldException x) {
                            throw new AssertionError(x);
                        }
                    }
                    @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                        return !referent.equals(reference) || !(referredFrom instanceof WeakReference);
                    }
                });
                if (!rootRefs.isEmpty()) {
                    fail(rootRefs.toString());
                } else {
                    System.err.println("Did not find any soft references to " + obj + ", looking for weak references…");
                    rootRefs = fromRoots(Set.of(obj), null, null, ScannerUtils.skipObjectsFilter(Set.of(reference), true));
                    if (!rootRefs.isEmpty()) {
                        fail(rootRefs.toString());
                    } else {
                        fail("Did not find any root references to " + obj + " whatsoever. Unclear why it is not being collected.");
                    }
                }
            }
        }
    }

    /**
     * TODO {@link LiveReferences#fromRoots(Collection, Set, BoundedRangeModel, Filter) logically ANDs the {@link Filter}
     * with {@link ScannerUtils#skipNonStrongReferencesFilter}, making it useless for our purposes.
     */
    private static Map<Object,Path> fromRoots(Collection<Object> objs, Set<Object> rootsHint, BoundedRangeModel progress, Filter f) {
        LiveEngine engine = new LiveEngine(progress) {
            // TODO InsaneEngine.processClass recognizes Class → ClassLoader but fails to notify the visitor,
            // so LiveEngine will fail to find a ClassLoader held only via one of its loaded classes.
            // The following trick substitutes for adding:
            // * to recognizeClass, before queue.add(cls): objects.getID(cls)
            // * to processClass, after recognize(cl): if (objects.isKnown(cl)) visitor.visitObjectReference(objects, cls, cl, null)
            // Also Path.getField confusingly returns "<changed>" when printing the Class → ClassLoader link.
            List<Class<?>> classes = new ArrayList<>();
            @Override public void visitClass(Class cls) {
                getID(cls);
                super.visitClass(cls);
                ClassLoader loader = cls.getClassLoader();
                if (loader != null) {
                    classes.add(cls);
                }
            }
            @Override public void visitObject(ObjectMap map, Object object) {
                super.visitObject(map, object);
                if (object instanceof ClassLoader) {
                    if (isKnown(object)) {
                        for (Class<?> c : classes) {
                            if (c.getClassLoader() == object) {
                                visitObjectReference(this, c, object, /* cannot get a Field for Class.classLoader, but unused here anyway */ null);
                            }
                        }
                    }
                }
            }
        };
        try {
            Field filter = LiveEngine.class.getDeclaredField("filter");
            filter.setAccessible(true);
            filter.set(engine, f);
        } catch (Exception x) {
            // The test has already failed at this point, so AssumptionViolatedException would inappropriately mark it as a skip.
            throw new AssertionError("could not patch INSANE", x);
        }
        
        // ScannerUtils.interestingRoots includes our own ClassLoader, thus any static fields in any classes loaded in any visible class…but not in the bootstrap classpath, since this has no ClassLoader object to traverse.
        Set<Object> rootsHint2 = new HashSet<>();
        if (rootsHint != null) {
            rootsHint2.addAll(rootsHint);
        }
        try {
            rootsHint2.add(Class.forName("java.io.ObjectStreamClass$Caches")); // http://stackoverflow.com/a/20461446/12916 or JDK-6232010 or http://www.szegedi.org/articles/memleak3.html
            rootsHint2.add(Class.forName("java.beans.ThreadGroupContext"));
        } catch (ClassNotFoundException x) {
            x.printStackTrace();
        }
        // TODO consider also: rootsHint2.add(Thread.getAllStackTraces().keySet()); // https://stackoverflow.com/a/3018672/12916

        return engine.trace(objs, rootsHint2);
    }

}

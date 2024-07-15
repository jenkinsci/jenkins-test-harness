/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allocates temporary directories and cleans it up at the end.
 * @author Kohsuke Kawaguchi
 */
public class TemporaryDirectoryAllocator {

    private static final Logger LOGGER = Logger.getLogger(TemporaryDirectoryAllocator.class.getName());

    /**
     * Remember allocated directories to delete them later.
     */
    private final Set<File> tmpDirectories = new HashSet<>();

    /**
     * Directory in which we allocate temporary directories.
     */
    private final File base;

    /**
     * Whether there should be a space character in the allocated temporary directories names.
     * It forces slaves created from a {@link JenkinsRule} to work inside a hazardous path,
     * which can help catching shell quoting bugs.<br>
     * If a particular test cannot be readily fixed to tolerate spaces, as a workaround try:
     * {@code @ClassRule public static TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true");}
     */
    private final boolean withoutSpace = Boolean.getBoolean("jenkins.test.noSpaceInTmpDirs");

    @Deprecated
    public TemporaryDirectoryAllocator(File base) {
        this.base = base;
    }

    public TemporaryDirectoryAllocator() {
        this.base = new File(System.getProperty("java.io.tmpdir"));
        base.mkdirs();
    }

    /**
     * Allocates a new empty temporary directory and returns it.
     *
     * This directory will be wiped out when {@link TemporaryDirectoryAllocator} gets disposed.
     * When this method returns, the directory already exists. 
     */
    public synchronized File allocate() throws IOException {
        try {
            File f = Files.createTempDirectory(base.toPath(), (withoutSpace ? "jkh" : "j h")).toFile();
            tmpDirectories.add(f);
            return f;
        } catch (IOException e) {
            throw new IOException("Failed to create a temporary directory in "+base,e);
        }
    }

    /**
     * Deletes all allocated temporary directories.
     */
    public synchronized void dispose() throws IOException, InterruptedException {
        for (File dir : tmpDirectories) {
            LOGGER.info(() -> "deleting " + dir);
            delete(dir.toPath());
        }
        tmpDirectories.clear();
    }

    /**
     * Deletes all allocated temporary directories asynchronously.
     */
    public synchronized void disposeAsync() {
        final Set<File> tbr = new HashSet<>(tmpDirectories);
        tmpDirectories.clear();

        new Thread("Disposing "+base) {
            @Override
            public void run() {
                for (File dir : tbr) {
                    LOGGER.info(() -> "deleting " + dir);
                    try {
                        delete(dir.toPath());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, null, e);
                    }
                }
            }
        }.start();
    }

    private void delete(Path p) throws IOException {
        LOGGER.fine(() -> "deleting " + p);
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(p)) {
                for (Path child : children) {
                    delete(child);
                }
            }
        }
        try {
            if (isWindows()) {
                // Windows throws an access denied exception when deleting read-only files
                boolean ok = p.toFile().setWritable(true);
                LOGGER.fine(() -> "allow write to " + p + ", result: " + ok);
            }
            Files.deleteIfExists(p);
        } catch (DirectoryNotEmptyException x) {
            try (Stream<Path> children = Files.list(p)) {
                throw new IOException(children.map(Path::toString).collect(Collectors.joining(" ")), x);
            }
        }
    }

    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}

/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * {@link SCM} useful for testing that extracts the given resource as a zip file.
 *
 * @author Kohsuke Kawaguchi
 */
public class ExtractResourceWithChangesSCM extends NullSCM {
    private final URL firstZip;
    private final URL secondZip;
    private final String moduleRoot;

    public ExtractResourceWithChangesSCM(URL firstZip, URL secondZip) {
        if ((firstZip == null) || (secondZip == null)) {
            throw new IllegalArgumentException();
        }
        this.firstZip = firstZip;
        this.secondZip = secondZip;
        this.moduleRoot = null;
    }

    public ExtractResourceWithChangesSCM(URL firstZip, URL secondZip, String moduleRoot) {
        if ((firstZip == null) || (secondZip == null)) {
            throw new IllegalArgumentException();
        }
        this.firstZip = firstZip;
        this.secondZip = secondZip;
        this.moduleRoot = moduleRoot;
    }

    @Override
    public FilePath getModuleRoot(FilePath workspace) {
        if (moduleRoot != null) {
            return workspace.child(moduleRoot);
        }
        return workspace;
    }

    @Override
    public boolean checkout(
            AbstractBuild<?, ?> build,
            Launcher launcher,
            FilePath workspace,
            BuildListener listener,
            @NonNull File changeLogFile)
            throws IOException, InterruptedException {
        if (workspace.exists()) {
            listener.getLogger().println("Deleting existing workspace " + workspace.getRemote());
            workspace.deleteRecursive();
        }
        listener.getLogger().println("Staging first zip: " + firstZip);
        workspace.unzipFrom(firstZip.openStream());
        listener.getLogger().println("Staging second zip: " + secondZip);
        workspace.unzipFrom(secondZip.openStream());

        // Get list of files changed in secondZip.
        ExtractChangeLogParser.ExtractChangeLogEntry changeLog =
                new ExtractChangeLogParser.ExtractChangeLogEntry(secondZip.toString());

        try (ZipInputStream zip = new ZipInputStream(secondZip.openStream())) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    changeLog.addFile(new ExtractChangeLogParser.FileInZip(e.getName()));
                }
            }
        }
        saveToChangeLog(changeLogFile, build.getCharset(), changeLog);

        return true;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new ExtractChangeLogParser();
    }

    /**
     * @deprecated use {@link #saveToChangeLog(File, Charset, ExtractChangeLogParser.ExtractChangeLogEntry)}
     */
    @Deprecated
    public void saveToChangeLog(File changeLogFile, ExtractChangeLogParser.ExtractChangeLogEntry changeLog)
            throws IOException {
        saveToChangeLog(changeLogFile, Charset.defaultCharset(), changeLog);
    }

    public void saveToChangeLog(
            File changeLogFile, Charset charset, ExtractChangeLogParser.ExtractChangeLogEntry changeLog)
            throws IOException {
        try (PrintStream ps = new PrintStream(changeLogFile, charset)) {
            ps.println(changeLog.getZipFile());
            for (String fileName : changeLog.getAffectedPaths()) {
                ps.println(fileName);
            }
        }
    }

    /**
     * Don't write 'this', so that subtypes can be implemented as anonymous class.
     */
    protected Object writeReplace() {
        return new Object();
    }

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return new SCMDescriptor<>(ExtractResourceWithChangesSCM.class, null) {};
    }
}

/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.EditType;
import hudson.scm.NullSCM;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.xml.sax.SAXException;

/**
 * Fake SCM implementation that can report arbitrary commits from arbitrary users.
 *
 * @author Kohsuke Kawaguchi
 */
public class FakeChangeLogSCM extends NullSCM implements Serializable {

    /**
     * Changes to be reported in the next build.
     */
    private List<EntryImpl> entries = new ArrayList<>();

    public EntryImpl addChange() {
        EntryImpl e = new EntryImpl();
        entries.add(e);
        return e;
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath remoteDir, TaskListener listener, File changeLogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        new FilePath(changeLogFile).touch(0);
        build.addAction(new ChangelogAction(entries, changeLogFile.getName()));
        entries = new ArrayList<>();
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new FakeChangeLogParser();
    }

    @Override public SCMDescriptor<?> getDescriptor() {
        return new SCMDescriptor<>(null) {};
    }

    public static class ChangelogAction extends InvisibleAction {
        private final List<EntryImpl> entries;
        private final String changeLogFile;

        public ChangelogAction(List<EntryImpl> entries, String changeLogFile) {
            this.entries = entries;
            this.changeLogFile = changeLogFile;
        }
    }

    public static class FakeChangeLogParser extends ChangeLogParser {
        @SuppressWarnings("rawtypes")
        @Override
        public FakeChangeLogSet parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
            for (ChangelogAction action : build.getActions(ChangelogAction.class)) {
                if (changelogFile.getName().equals(action.changeLogFile)) {
                    return new FakeChangeLogSet(build, action.entries);
                }
            }
            return new FakeChangeLogSet(build, List.of());
        }
    }

    public static class FakeChangeLogSet extends ChangeLogSet<EntryImpl> implements Serializable {
        private List<EntryImpl> entries;

        public FakeChangeLogSet(Run<?, ?> build, List<EntryImpl> entries) {
            super(build, null);
            this.entries = entries;
        }

        @Override
        public boolean isEmptySet() {
            return entries.isEmpty();
        }

        @Override
        public Iterator<EntryImpl> iterator() {
            return entries.iterator();
        }

        private static final long serialVersionUID = 1L;
    }

    public static class EntryImpl extends Entry implements Serializable {
        private String msg = "some commit message";
        private String author = "someone";
        private String path = "path";

        public EntryImpl withAuthor(String author) {
            this.author = author;
            return this;
        }

        public EntryImpl withMsg(String msg) {
            this.msg = msg;
            return this;
        }

        public EntryImpl withPath(String path) {
            this.path = path;
            return this;
        }

        @Override
        public String getMsg() {
            return msg;
        }

        @Override
        public User getAuthor() {
            return User.get(author);
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return Set.of(path);
        }

        @Override
        public Collection<ChangeLogSet.AffectedFile> getAffectedFiles() {
            ChangeLogSet.AffectedFile affectedFile = new ChangeLogSet.AffectedFile() {
                @Override
                public String getPath() {
                    return path;
                }

                @Override
                public EditType getEditType() {
                    return EditType.EDIT;
                }
            };
            return Set.of(affectedFile);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}

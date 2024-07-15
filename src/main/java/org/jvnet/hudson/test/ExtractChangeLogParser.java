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

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.xml.sax.SAXException;

/**
 * @author Andrew Bayer
 */
public class ExtractChangeLogParser extends ChangeLogParser {
    @SuppressWarnings("rawtypes")
    @Override
    public ExtractChangeLogSet parse(AbstractBuild build, File changeLogFile) throws IOException, SAXException {
        if (changeLogFile.exists()) {
            ExtractChangeLogSet logSet;
            try (FileInputStream fis = new FileInputStream(changeLogFile)) {
                logSet = parse(build, fis);
            }
            return logSet;
        } else {
            return new ExtractChangeLogSet(build, new ArrayList<>());
        }
    }

    @SuppressWarnings("rawtypes")
    public ExtractChangeLogSet parse(AbstractBuild build, InputStream changeLogStream) throws IOException, SAXException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(changeLogStream, build.getCharset()))) {
            ExtractChangeLogEntry entry = new ExtractChangeLogEntry(br.readLine());
            String fileName;
            while ((fileName = br.readLine()) != null) {
                entry.addFile(new FileInZip(fileName));
            }
            return new ExtractChangeLogSet(build, List.of(entry));
        }
    }


    @ExportedBean(defaultVisibility = 999)
    public static class ExtractChangeLogEntry extends ChangeLogSet.Entry {
        private final List<FileInZip> files = new ArrayList<>();
        private final String zipFile;

        ExtractChangeLogEntry(String zipFile) {
            this.zipFile = zipFile;
        }

        @Exported
        public String getZipFile() {
            return zipFile;
        }

        @Override
        public void setParent(ChangeLogSet parent) {
            super.setParent(parent);
        }

        @Override
        public Collection<String> getAffectedPaths() {
            Collection<String> paths = new ArrayList<>(files.size());
            for (FileInZip file : files) {
                paths.add(file.getFileName());
            }
            return paths;
        }

        @Override
        @Exported
        public User getAuthor() {
            return User.get("testuser");
        }

        @Override
        @Exported
        public String getMsg() {
            return "Extracted from " + zipFile;
        }

        public void addFile(FileInZip fileName) {
            files.add(fileName);
        }

    }

    @ExportedBean(defaultVisibility = 999)
    public static class FileInZip {
        private final String fileName;

        FileInZip(String fileName) {
            this.fileName = fileName;
        }

        @Exported
        public String getFileName() {
            return fileName;
        }

    }

}

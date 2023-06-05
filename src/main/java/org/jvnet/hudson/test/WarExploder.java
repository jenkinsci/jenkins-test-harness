/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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
import hudson.FilePath;
import hudson.remoting.Which;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

/**
 * Ensures that {@code jenkins.war} is exploded.
 *
 * <p>
 * Depending on where the test is run (for example, inside Maven vs IDE), this code attempts to
 * use jenkins.war from the right place, thereby improving the productivity.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WarExploder {

    private static final Logger LOGGER = Logger.getLogger(WarExploder.class.getName());

    public static final String JENKINS_WAR_PATH_PROPERTY_NAME = "jth.jenkins-war.path";
    @CheckForNull
    private static final String JENKINS_WAR_PATH = System.getProperty(JENKINS_WAR_PATH_PROPERTY_NAME);

    /**
     * A pattern that matches hex encoded strings.
     */
    private static final Pattern HEX_DIGITS = Pattern.compile("^[a-f0-9]+$");

    public static synchronized File getExplodedDir() throws Exception {
        if (EXPLODE_DIR == null) {
            EXPLODE_DIR = explode();
        }
        return EXPLODE_DIR;
    }

    private static File EXPLODE_DIR;

    static File findJenkinsWar() throws Exception {
        File war;
        if (JENKINS_WAR_PATH != null) {
            war = new File(JENKINS_WAR_PATH).getAbsoluteFile();
            LOGGER.log(Level.INFO, "Using a predefined WAR file {0} define by the {1} system property",
                    new Object[] {war, JENKINS_WAR_PATH_PROPERTY_NAME});
            if (!war.exists()) {
                throw new IOException("A Predefined WAR file path does not exist: " + war);
            } else if (!war.isFile()) {
                throw new IOException("A Predefined WAR file path does not point to a file: " + war);
            }
        } else {
            // locate jenkins.war
            URL winstone = WarExploder.class.getResource("/executable/winstone.jar");
            if (winstone != null) {
                war = Which.jarFile(Class.forName("executable.Main"));
            } else {
                // JENKINS-45245: work around incorrect test classpath in IDEA. Note that this will not correctly handle timestamped snapshots; in that case use `mvn test`.
                File core = Which.jarFile(Jenkins.class); // will fail with IllegalArgumentException if have neither jenkins-war.war nor jenkins-core.jar in ${java.class.path}
                String version;
                File coreArtifactDir;
                if (HEX_DIGITS.matcher(core.getParentFile().getName()).matches()) {
                    // Gradle
                    version = core.getParentFile().getParentFile().getName();
                    coreArtifactDir = core.getParentFile().getParentFile().getParentFile();
                } else {
                    // Maven
                    version = core.getParentFile().getName();
                    coreArtifactDir = core.getParentFile().getParentFile();
                }
                if (core.getName().equals("jenkins-core-" + version + ".jar") && coreArtifactDir.getName().equals("jenkins-core")) {
                    File warArtifactDir = new File(coreArtifactDir.getParentFile(), "jenkins-war");
                    war = new File(new File(warArtifactDir, version), "jenkins-war-" + version + ".war");
                    if (!war.isFile()) {
                        File[] hashes = new File(warArtifactDir, version).listFiles();
                        if (hashes != null) {
                            for (File hash : hashes) {
                                if (HEX_DIGITS.matcher(hash.getName()).matches()) {
                                    war = new File(hash, "jenkins-war-" + version + ".war");
                                    if (war.isFile()) {
                                        break;
                                    }
                                }
                            }
                        }
                        if (!war.isFile()) {
                            throw new AssertionError(war + " does not yet exist. Prime your development environment by running `mvn validate`.");
                        }
                    }
                    LOGGER.log(Level.FINE, "{0} is the continuation of the classpath by other means", war);
                } else {
                    throw new AssertionError(core + " is not in the expected location, and jenkins-war-*.war was not in " + System.getProperty("java.class.path"));
                }
            }
        }
        return war;
    }

    /**
     * Explodes jenkins.war, if necessary, and returns its root dir.
     */
    private static File explode() throws Exception {
        // are we in the Jenkins main workspace? If so, pick up hudson/main/war/resources
        // this saves the effort of packaging a war file and makes the debug cycle faster

        File d = new File(".").getAbsoluteFile();

        for( ; d!=null; d=d.getParentFile()) {
            if(new File(d,".jenkins").exists()) {
                File dir = new File(d,"war/target/jenkins");
                if(dir.exists()) {
                    LOGGER.log(Level.INFO, "Using jenkins.war resources from {0}", dir);
                    return dir;
                }
            }
        }

        final File war = findJenkinsWar();

        // TODO this assumes that the CWD of the Maven process is the plugin ${basedir}, which may not be the case
        File buildDirectory = new File(System.getProperty("buildDirectory", "target"));
        File explodeDir = new File(buildDirectory, "jenkins-for-test").getAbsoluteFile();
        explodeDir.getParentFile().mkdirs();
        while (new File(explodeDir + ".exploding").isFile()) {
            explodeDir = new File(explodeDir + "x");
        }
        File timestamp = new File(explodeDir,".timestamp");

        if(!timestamp.exists() || (timestamp.lastModified()!=war.lastModified())) {
            LOGGER.log(Level.INFO, "Exploding {0} into {1}", new Object[] {war, explodeDir});
            new FileOutputStream(explodeDir + ".exploding").close();
            new FilePath(explodeDir).deleteRecursive();
            new FilePath(war).unzip(new FilePath(explodeDir));
            if(!explodeDir.exists())    // this is supposed to be impossible, but I'm investigating HUDSON-2605
                throw new IOException("Failed to explode "+war);
            new FileOutputStream(timestamp).close();
            timestamp.setLastModified(war.lastModified());
            new File(explodeDir + ".exploding").delete();
        } else {
            LOGGER.log(Level.INFO, "Picking up existing exploded jenkins.war at {0}", explodeDir.getAbsolutePath());
        }

        return explodeDir;
    }

    private WarExploder() {}

}

package org.jvnet.hudson.test;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.List;

/**
 * Slave that pretends to fork processes.
 *
 * @author Kohsuke Kawaguchi
 * @see HudsonTestCase#createPretendSlave(FakeLauncher)
 */
public class PretendSlave extends Slave {
    private transient FakeLauncher faker;

    /**
     * Number of processed that are launched.
     */
    public int numLaunch;

    public PretendSlave(
            String name,
            String remoteFS,
            int numExecutors,
            Mode mode,
            String labelString,
            ComputerLauncher launcher,
            FakeLauncher faker)
            throws IOException, FormException {
        super(
                name,
                "pretending a slave",
                remoteFS,
                String.valueOf(numExecutors),
                mode,
                labelString,
                launcher,
                RetentionStrategy.NOOP,
                List.of());
        this.faker = faker;
    }

    public PretendSlave(String name, String remoteFS, String labelString, ComputerLauncher launcher, FakeLauncher faker)
            throws IOException, FormException {
        this(name, remoteFS, 1, Mode.NORMAL, labelString, launcher, faker);
    }

    @NonNull
    @Override
    public Launcher createLauncher(TaskListener listener) {
        return new LocalLauncher(listener) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                synchronized (PretendSlave.this) {
                    numLaunch++;
                }
                Proc p = faker.onLaunch(starter);
                if (p != null) {
                    return p;
                }
                return super.launch(starter);
            }
        };
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {}
}

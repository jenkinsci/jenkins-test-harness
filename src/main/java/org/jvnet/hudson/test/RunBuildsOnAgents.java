/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.QueueListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Causes builds in tests to run on agents by default even when the test does not specifically request this.
 */
public class RunBuildsOnAgents {

    private static final Logger LOGGER = Logger.getLogger(RunBuildsOnAgents.class.getName());

    @Initializer(after = InitMilestone.JOB_LOADED) public static void addMockCloud() throws IOException {
        Jenkins.get().setMode(Node.Mode.EXCLUSIVE);
        Jenkins.get().clouds.add(new MockCloud());
    }

    // adapted from mock-slave-plugin
    public static final class MockCloud extends Cloud {

        private static final Logger LOGGER = Logger.getLogger(MockCloud.class.getName());

        private static final AtomicLong counter = new AtomicLong();

        @DataBoundConstructor public MockCloud() {
            super("mock");
        }

        @Override public boolean canProvision(CloudState state) {
            return true;
        }

        @Override public Collection<NodeProvisioner.PlannedNode> provision(CloudState state, int excessWorkload) {
            Collection<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            while (excessWorkload > 0) {
                final long cnt = counter.incrementAndGet();
                LOGGER.info("will create a mock agent");
                r.add(new NodeProvisioner.PlannedNode("Mock Agent #" + cnt, Computer.threadPoolForRemoting.submit(() -> new MockCloudSlave("mock-agent-" + cnt, Node.Mode.NORMAL, 1, "", true)), 1));
                excessWorkload -= 1;
            }
            LOGGER.log(Level.FINE, "planning to provision {0} agents", r.size());
            return r;
        }

        @Extension public static final class DescriptorImpl extends Descriptor<Cloud> {}

        private static final class MockCloudSlave extends AbstractCloudSlave {

            MockCloudSlave(String slaveName, Node.Mode mode, int numExecutors, String labelString, boolean oneShot) throws Descriptor.FormException, IOException {
                super(slaveName, new File(new File(Jenkins.get().getRootDir(), "mock-agents"), slaveName).getAbsolutePath(), new MockSlaveLauncher());
                setRetentionStrategy(new CloudRetentionStrategy(1));
                LOGGER.info("creating a mock agent");
            }

            @Override public FilePath getWorkspaceFor(TopLevelItem item) {
                FilePath master = Jenkins.get().getWorkspaceFor(item);
                try {
                    if (master.isDirectory()) {
                        LOGGER.info(() -> "returning " + master);
                        return createPath(master.getRemote());
                    } else {
                        LOGGER.info(() -> master + " does not exist");
                        // This trick does not work in case content is copied after the build starts: https://github.com/jenkinsci/docker-commons-plugin/blob/7078a70448719715ae2c91b6a7fd718de76cf5a2/src/test/java/org/jenkinsci/plugins/docker/commons/credentials/DockerServerCredentialsBindingTest.java#L99-L106
                        // or if the test expects content to be in the workspace after completion: https://github.com/jenkinsci/credentials-binding-plugin/blob/07564b60dd76a818df3f8903cf0b43f1539f527c/src/test/java/org/jenkinsci/plugins/credentialsbinding/impl/BindingStepTest.java#L167-L171
                        // One alternative would be to unconditionally use the master workspace; but this could break tests trying to use two executors at once.
                    }
                } catch (IOException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
                return super.getWorkspaceFor(item);
            }

            @Override public AbstractCloudComputer<?> createComputer() {
                return new MockCloudComputer(this);
            }

            @Override protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
                // need do nothing
            }

            @Extension public static final class DescriptorImpl extends Slave.SlaveDescriptor {

                @Override public boolean isInstantiable() {
                    return false;
                }

            }

        }

        private static final class MockCloudComputer extends AbstractCloudComputer<MockCloudSlave> {

            MockCloudComputer(MockCloudSlave slave) {
                super(slave);
            }

        }

        public static class MockSlaveLauncher extends ComputerLauncher {

            @Override public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                LOGGER.info("launching mock agent");
                listener.getLogger().println("Launching");
                File slaveJar = Which.jarFile(Which.class);
                if (!slaveJar.isFile()) {
                    slaveJar = File.createTempFile("slave", ".jar");
                    slaveJar.deleteOnExit();
                    FileUtils.copyURLToFile(new Slave.JnlpJar("slave.jar").getURL(), slaveJar);
                }
                final EnvVars cookie = EnvVars.createCookie();
                ProcessBuilder pb = new ProcessBuilder("java", "-jar", slaveJar.getAbsolutePath());
                pb.environment().putAll(cookie);
                Process proc = pb.start();
                InputStream is = proc.getInputStream();
                OutputStream os = proc.getOutputStream();
                new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(), proc.getErrorStream(), listener.getLogger()).start();
                computer.setChannel(is, os, listener.getLogger(), new Channel.Listener() {
                    @Override
                    public void onClosed(Channel channel, IOException cause) {
                        Jenkins j = Jenkins.getInstanceOrNull();
                        if (j == null || j.isTerminating()) {
                            LOGGER.log(Level.INFO, "Leaving processes running on {0} during shutdown", computer.getName());
                        } else {
                            LOGGER.log(Level.FINE, "Killing any processes still running on {0}", computer.getName());
                            try {
                                ProcessTree.get().killAll(proc, cookie);
                            } catch (InterruptedException e) {
                                LOGGER.log(Level.INFO, "interrupted", e);
                            }
                        }
                    }
                });
                LOGGER.log(Level.INFO, "agent launched for {0}", computer.getDisplayName());
            }

            @Symbol("mock")
            @Extension public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
                @Override public String getDisplayName() {
                    return "Mock Agent Launcher";
                }
            }

            @Extension public static class Listener extends ComputerListener {
                static final Map<Computer, Long> launchTimes = new WeakHashMap<Computer, Long>();
                @Override
                public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
                    Long launchTime = launchTimes.remove(c);
                    if (launchTime != null) {
                        long seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - launchTime);
                        listener.getLogger().printf("Launched in %ds%n", seconds);
                        LOGGER.log(Level.INFO, "Launched {0} in {1}s", new Object[] {c.getName(), seconds});
                    }
                }
            }

        }

    }

    // copied from kubernetes-plugin
    @Extension(ordinal = 100) public static class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {

        @Override public NodeProvisioner.StrategyDecision apply(NodeProvisioner.StrategyState strategyState) {
            final Label label = strategyState.getLabel();

            LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
            int availableCapacity = snapshot.getAvailableExecutors() + snapshot.getConnectingExecutors() + strategyState.getPlannedCapacitySnapshot() + strategyState.getAdditionalPlannedCapacity();
            int previousCapacity = availableCapacity;
            int currentDemand = snapshot.getQueueLength();
            LOGGER.log(Level.FINE, "Available capacity={0}, currentDemand={1}",
                    new Object[] {availableCapacity, currentDemand});
            if (availableCapacity < currentDemand) {
                List<Cloud> jenkinsClouds = new ArrayList<>(Jenkins.get().clouds);
                Collections.shuffle(jenkinsClouds);
                Cloud.CloudState cloudState = new Cloud.CloudState(label, strategyState.getAdditionalPlannedCapacity());
                for (Cloud cloud : jenkinsClouds) {
                    int workloadToProvision = currentDemand - availableCapacity;
                    if (!(cloud instanceof MockCloud)) {
                        continue;
                    }
                    if (!cloud.canProvision(cloudState)) {
                        continue;
                    }
                    for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
                        if (cl.canProvision(cloud, cloudState, workloadToProvision) != null) {
                            continue;
                        }
                    }
                    Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(cloudState, workloadToProvision);
                    LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());
                    fireOnStarted(cloud, strategyState.getLabel(), plannedNodes);
                    strategyState.recordPendingLaunches(plannedNodes);
                    availableCapacity += plannedNodes.size();
                    LOGGER.log(Level.FINE, "After provisioning, available capacity={0}, currentDemand={1}", new Object[] {availableCapacity, currentDemand});
                    break;
                }
            }
            if (availableCapacity > previousCapacity) {
                LOGGER.log(Level.FINE, "Suggesting NodeProvisioner review");
                Timer.get().schedule(Jenkins.get().unlabeledNodeProvisioner::suggestReviewNow, 1L, TimeUnit.SECONDS);
            }
            if (availableCapacity >= currentDemand) {
                LOGGER.log(Level.FINE, "Provisioning completed");
                return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
            } else {
                LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
                return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
            }
        }

        private static void fireOnStarted(final Cloud cloud, final Label label,
                                          final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
            for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
                try {
                    cl.onStarted(cloud, label, plannedNodes);
                } catch (Error e) {
                    throw e;
                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while " +
                            "processing onStarted() listener call in " + cl + " for label " +
                            label.toString(), e);
                }
            }
        }

        /**
         * Ping the nodeProvisioner as a new task enters the queue.
         */
        @Extension public static class FastProvisioning extends QueueListener {

            @Override public void onEnterBuildable(Queue.BuildableItem item) {
                final Jenkins jenkins = Jenkins.get();
                final Label label = item.getAssignedLabel();
                for (Cloud cloud : jenkins.clouds) {
                    if (cloud instanceof MockCloud && cloud.canProvision(new Cloud.CloudState(label, 0))) {
                        final NodeProvisioner provisioner = (label == null
                                ? jenkins.unlabeledNodeProvisioner
                                : label.nodeProvisioner);
                        provisioner.suggestReviewNow();
                    }
                }
            }
        }

    }

    private RunBuildsOnAgents() {}

}

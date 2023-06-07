package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.Main;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Runs at the end of the test to cleanup any live channels.
 *
* @author Kohsuke Kawaguchi
*/
@Extension
public class ChannelShutdownListener extends ComputerListener implements EndOfTestListener {
    /**
     * Remember channels that are created, to release them at the end.
     */
    private List<Channel> channels = new ArrayList<>();

    public ChannelShutdownListener() {
        if (!Main.isUnitTest) {
            Logger.getLogger(ChannelShutdownListener.class.getName()).severe(() -> "JENKINS-58771: Jenkins test harness code being loaded in what seems to be a production system, perhaps causing critical memory leaks: " + ChannelShutdownListener.class.getProtectionDomain().getCodeSource().getLocation());
        }
    }

    @Override
    public synchronized void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        VirtualChannel ch = c.getChannel();
        if (ch instanceof Channel) {
            channels.add((Channel)ch);
        }
    }

    @Override
    public synchronized void onTearDown() throws Exception {
        for (Channel c : channels)
            c.close();
        for (Channel c : channels)
            c.join();
        channels.clear();
    }
}

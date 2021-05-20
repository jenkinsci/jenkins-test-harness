package org.jvnet.hudson.test;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.ThreadPool;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class ThreadPoolImpl extends AbstractLifeCycle implements ThreadPool {
    private final ExecutorService es;

    public ThreadPoolImpl(ExecutorService es) {
        this.es = es;
    }

    @Override
    public void execute(@Nonnull Runnable job) {
        if (!isRunning() || job==null)
            throw new RejectedExecutionException();

        es.submit(job);
    }

    public void join() throws InterruptedException {
        while(!es.awaitTermination(999 * 60 * 60 * 24, TimeUnit.SECONDS)) {
            // noop
        }
    }

    public int getThreads() {
        return 999;
    }

    public int getIdleThreads() {
        return 999;
    }

    public boolean isLowOnThreads() {
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        es.shutdown();
    }
}

package org.jvnet.hudson.test;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.ThreadPool;

/**
 * @author Kohsuke Kawaguchi
 */
public class ThreadPoolImpl extends AbstractLifeCycle implements ThreadPool {
    private final ExecutorService es;

    public ThreadPoolImpl(ExecutorService es) {
        this.es = es;
    }

    @Override
    public void execute(@NonNull Runnable job) {
        if (!isRunning() || job==null)
            throw new RejectedExecutionException();

        es.submit(job);
    }

    @Override
    public void join() throws InterruptedException {
        while(!es.awaitTermination(TimeUnit.DAYS.toSeconds(999), TimeUnit.SECONDS)) {
            // noop
        }
    }

    @Override
    public int getThreads() {
        return 999;
    }

    @Override
    public int getIdleThreads() {
        return 999;
    }

    @Override
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

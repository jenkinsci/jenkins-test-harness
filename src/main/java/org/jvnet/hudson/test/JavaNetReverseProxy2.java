package org.jvnet.hudson.test;

import hudson.Util;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * Acts as a reverse proxy, so that during a test we can avoid hitting updates.jenkins.io.
 *
 * <p>
 * The contents are cached locally.
 *
 * @author Kohsuke Kawaguchi
 */
public class JavaNetReverseProxy2 extends HttpServlet {
    private final Server server;
    public final int localPort;

    private final File cacheFolder;

    public JavaNetReverseProxy2(File cacheFolder) throws Exception {
        this.cacheFolder = cacheFolder;
        cacheFolder.mkdirs();
        Runtime.getRuntime().addShutdownHook(new Thread("delete " + cacheFolder) {
            @Override
            public void run() {
                try {
                    Util.deleteRecursive(cacheFolder);
                } catch (IOException x) {
                    x.printStackTrace();
                }
            }
        });

        QueuedThreadPool qtp = new QueuedThreadPool();
        qtp.setName("Jetty (JavaNetReverseProxy)");
        server = new Server(qtp);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        ServletContextHandler root = new ServletContextHandler(contexts, "/", ServletContextHandler.SESSIONS);
        root.addServlet(new ServletHolder(this), "/");

        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();
    }

    public void stop() throws Exception {
        server.stop();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        String d = Util.getDigestOf(path);

        File cache = new File(cacheFolder, d);
        synchronized (this) {
            if (!cache.exists()) {
                URL url = new URL("https://updates.jenkins.io/" + path);
                FileUtils.copyURLToFile(url, cache);
            }
        }

        resp.setContentType(getMimeType(path));
        Files.copy(cache.toPath(), resp.getOutputStream());
    }

    private String getMimeType(String path) {
        int idx = path.indexOf('?');
        if (idx >= 0) {
            path = path.substring(0, idx);
        }
        if (path.endsWith(".json")) {
            return "text/javascript";
        }
        return getServletContext().getMimeType(path);
    }

    private static volatile JavaNetReverseProxy2 INSTANCE;

    /**
     * Gets the default instance.
     */
    public static synchronized JavaNetReverseProxy2 getInstance() throws Exception {
        if (INSTANCE == null) {
            INSTANCE = new JavaNetReverseProxy2(
                    new File(new File(System.getProperty("java.io.tmpdir")), "jenkins.io-cache2"));
        }
        return INSTANCE;
    }
}

package org.jvnet.hudson.test;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
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

        QueuedThreadPool qtp = new QueuedThreadPool();
        qtp.setName("Jetty (JavaNetReverseProxy)");
        server = new Server(qtp);

        if (_isEE10Plus()) {
            ServletContextHandler context = new ServletContextHandler();
            context.addServlet(new ServletHolder(this), "/");
            server.setHandler(context);
        } else {
            org.eclipse.jetty.ee9.servlet.ServletContextHandler context =
                    new org.eclipse.jetty.ee9.servlet.ServletContextHandler();
            context.addServlet(new org.eclipse.jetty.ee9.servlet.ServletHolder(this), "/");
            server.setHandler(context);
        }

        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();
    }

    public void stop() throws Exception {
        server.stop();
    }

    @Override
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "TODO needs triage")
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Path parent = cacheFolder.toPath();
        Files.createDirectories(parent);

        String path = req.getServletPath();
        String d = Util.getDigestOf(path);
        Path cache = parent.resolve(d);

        if (!Files.exists(cache)) {
            Path tmp = Files.createTempFile(parent, d, null);
            try {
                URL url = new URL("https://updates.jenkins.io/" + path);
                try (InputStream is = url.openStream()) {
                    Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(tmp, cache, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    try {
                        Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e2) {
                        e2.addSuppressed(e);
                        throw e2;
                    }
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        resp.setContentType(getMimeType(path));
        Files.copy(cache, resp.getOutputStream());
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

    private static boolean _isEE10Plus() {
        try {
            ServletRequest.class.getDeclaredMethod("getRequestId");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}

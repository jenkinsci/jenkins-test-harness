/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package org.jvnet.hudson.test.jetty;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Defines a way for {@link JenkinsRule} to run Jetty. This permits the test harness to select the appropriate Jakarta
 * EE version based on the version of Jenkins core under test. The constructor should try to link against everything
 * necessary so any errors are thrown up front.
 *
 * @author Basil Crow
 */
public abstract class JettyProvider {

    protected Server server;

    public record Context(ServletContext servletContext, int localPort, Server server, File explodedWarDir) {}

    /**
     * Prepares a webapp hosting environment and returns a context containing the {@link ServletContext}, port, and
     * server for testing.
     */
    public abstract Context createWebServer(
            int localPort, String contextPath, Supplier<LoginService> loginServiceSupplier) throws Exception;

    protected final void createServer() {
        QueuedThreadPool qtp = new QueuedThreadPool();
        qtp.setName("Jetty (JenkinsRule)");
        server = new Server(qtp);
    }

    protected final ServerConnector createConnector(int localPort) {
        ServerConnector connector = new ServerConnector(server);
        HttpConfiguration config =
                connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
        config.setRequestHeaderSize(12 * 1024);
        config.setHttpCompliance(HttpCompliance.RFC7230);
        config.setUriCompliance(UriCompliance.LEGACY);
        connector.setHost("localhost");
        if (System.getProperty("port") != null) {
            connector.setPort(Integer.parseInt(System.getProperty("port")));
        } else if (localPort != 0) {
            connector.setPort(localPort);
        }
        server.addConnector(connector);
        return connector;
    }

    protected final void configureCompression(Handler handler) {
        String compression = System.getProperty("jth.compression", "gzip");
        if (compression.equals("gzip")) {
            GzipHandler gzipHandler = new GzipHandler();
            gzipHandler.setHandler(handler);
            server.setHandler(gzipHandler);
        } else if (compression.equals("none")) {
            server.setHandler(handler);
        } else {
            throw new IllegalArgumentException("Unexpected compression scheme: " + compression);
        }
    }
}

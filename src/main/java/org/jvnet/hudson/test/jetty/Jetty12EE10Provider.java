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

import jakarta.servlet.ServletRequest;
import java.io.File;
import java.util.function.Supplier;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.jvnet.hudson.test.NoListenerConfiguration2;
import org.jvnet.hudson.test.WarExploder;
import org.kohsuke.MetaInfServices;

@MetaInfServices(JettyProvider.class)
public final class Jetty12EE10Provider extends JettyProvider {

    public Jetty12EE10Provider() {
        try {
            ServletRequest.class.getDeclaredMethod("getRequestId");
        } catch (NoSuchMethodException e) {
            NoSuchMethodError error = new NoSuchMethodError();
            error.initCause(e);
            throw error;
        }
    }

    @Override
    public Context createWebServer(int localPort, String contextPath, Supplier<LoginService> loginServiceSupplier)
            throws Exception {
        createServer();

        File explodedWarDir = WarExploder.getExplodedDir();
        WebAppContext context = new WebAppContext(explodedWarDir.getPath(), contextPath) {
            @Override
            public void preConfigure() throws Exception {
                super.preConfigure();
                getServletHandler().setDecodeAmbiguousURIs(true);
            }

            @Override
            protected ClassLoader configureClassLoader(ClassLoader loader) {
                // Use flat classpath in tests
                return loader;
            }
        };
        context.setBaseResource(ResourceFactory.of(context).newResource(explodedWarDir.getPath()));
        context.setClassLoader(getClass().getClassLoader());
        context.setConfigurationDiscovered(true);
        context.addBean(new NoListenerConfiguration2(context));
        context.setServer(server);
        context.getSecurityHandler().setLoginService(loginServiceSupplier.get());
        JettyWebSocketServletContainerInitializer.configure(context, null);

        configureCompression(context);
        ServerConnector connector = createConnector(localPort);
        server.start();
        return new Context(context.getServletContext(), connector.getLocalPort(), server, explodedWarDir);
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.WebAppMain;
import java.util.EventListener;
import javax.servlet.ServletContextListener;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Kills off the {@link WebAppMain} {@link ServletContextListener}.
 *
 * <p>
 * This is so that the harness can create the {@link jenkins.model.Jenkins} object.
 * with the home directory of our choice.
 *
 * @author Kohsuke Kawaguchi
 */
public class NoListenerConfiguration extends AbstractLifeCycle {
    private final WebAppContext context;

    public NoListenerConfiguration(WebAppContext context) {
        this.context = context;
    }

    @Override
    protected void doStart() {
        for (EventListener eventListener : context.getEventListeners()) {
            if (eventListener instanceof WebAppMain) {
                context.removeEventListener(eventListener);
            }
        }
    }
}

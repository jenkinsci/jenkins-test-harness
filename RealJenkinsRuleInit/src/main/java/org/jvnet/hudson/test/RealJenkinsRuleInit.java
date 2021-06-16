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

import hudson.util.PluginServletFilter;
import hudson.Plugin;
import java.net.URL;
import java.net.URLClassLoader;
import jenkins.model.Jenkins;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public class RealJenkinsRuleInit extends Plugin {

    @SuppressWarnings("deprecation") // @Initializer just gets run too late, even with before = InitMilestone.PLUGINS_PREPARED
    public RealJenkinsRuleInit() {}

    @Override
    public void start() throws Exception {
        /*
        According to ServletRequest documentation:
            If the parameter data was sent in the request body, such as occurs with an HTTP POST request,
            then reading the body directly via getInputStream or getReader can interfere with the execution of this method.
        Check RealJenkinsRule#runRemotely, it has
         conn.setRequestProperty("Content-Type", "text/plain; utf-8");

         to avoid this issue.
        */
        PluginServletFilter.addFilter(new Filter() {

          @Override
          public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
              String fake = request.getParameter("fake");
              chain.doFilter(request, response);
          }

          @Override
          public void destroy() {}

          @Override
          public void init(FilterConfig filterConfig) throws ServletException {}
        });

        new URLClassLoader(new URL[] {new URL(System.getProperty("RealJenkinsRule.location"))}, ClassLoader.getSystemClassLoader().getParent()).
                loadClass("org.jvnet.hudson.test.RealJenkinsRule$Init2").
                getMethod("run", Object.class).
                invoke(null, Jenkins.get());
    }

}

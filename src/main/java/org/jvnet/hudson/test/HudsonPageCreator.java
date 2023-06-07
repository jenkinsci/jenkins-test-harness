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

import java.io.IOException;
import java.util.Locale;
import org.htmlunit.DefaultPageCreator;
import org.htmlunit.Page;
import org.htmlunit.PageCreator;
import org.htmlunit.WebResponse;
import org.htmlunit.WebWindow;

/**
 * {@link PageCreator} that understands JNLP file.
 * 
 * @author Kohsuke Kawaguchi
 */
public class HudsonPageCreator extends DefaultPageCreator {
    @Override
    public Page createPage(WebResponse webResponse, WebWindow webWindow) throws IOException {
        String contentType = webResponse.getContentType().toLowerCase(Locale.ENGLISH);
        if (contentType.equals("application/x-java-jnlp-file")) {
            return createXmlPage(webResponse, webWindow);
        }
        // Need to sidestep HtmlUnit default behaviour here. It defaults the response type to
        // being text/plain (and so creates a TextPage) if the content type in the response is
        // blank + is an empty response.
        if (contentType.isEmpty()) {
            return createHtmlPage(webResponse, webWindow);
        }

        return super.createPage(webResponse, webWindow);
    }

    public static final HudsonPageCreator INSTANCE = new HudsonPageCreator();
}

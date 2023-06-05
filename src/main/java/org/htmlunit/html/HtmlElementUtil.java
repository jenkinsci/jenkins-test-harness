/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package org.htmlunit.html;

import java.io.IOException;
import java.util.Set;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebClientUtil;

/**
 * {@link HtmlElement} helper methods.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HtmlElementUtil {

    /**
     * Click on the supplied element.
     * <p>
     * Waits for all executing JavaScript tasks to complete before returning.
     *     
     * @param element The element to click.
     * @return The page resulting from the click
     * @throws IOException if an IO error occurs
     */
    public static Page click(HtmlElement element) throws IOException {
        if (element == null) {
            return null;
        }
        
        try {
            return element.click();
        } finally {
            // The JS script execution tasks are added to a queue and executed
            // async. Wait for all to finish executing.
            WebClient webClient = element.getPage().getWebClient();
            WebClientUtil.waitForJSExec(webClient);
        }
    }

    /**
     * Does the supplied element define the specified HTML "class" name.
     * @param element The element to check.
     * @param className The HTML "class" name to check for.
     * @return {@code true} if the element defines the specified class, otherwise {@code false}.
     */
    public static boolean hasClassName(HtmlElement element, String className) {
        String classAttribute = element.getAttribute("class");
        Set<String> classes = Set.of(classAttribute.split(" "));
        return classes.contains(className);
    }
}

package org.jvnet.hudson.test;

import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;

import java.util.concurrent.TimeUnit;

// TODO - This might need to be moved to a more appropriate package
public class QueryUtils {

    /**
     * Waits until the given string is visible on the page, otherwise throws an exception
     * @param page the page
     * @param value the value to find
     * @throws RuntimeException if string is not present after three seconds
     */
    public static void waitUntilStringIsPresent(HtmlPage page, String value) {
        long maxWaitTime = TimeUnit.SECONDS.toMillis(3);
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            if (page.querySelector("*").getTextContent().contains(value)) {
                System.out.println("Took '" + (System.currentTimeMillis() - startTime) + "' milliseconds " +
                        "until string '" + value + "' was present");
                return;
            } else {
                try {
                    Thread.sleep(100); // Adjust the interval as needed
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        throw new RuntimeException("String '" + value + "' was not present '" + value + "' after '" + maxWaitTime + "' seconds");
    }

    /**
     * Waits until the given string is not visible on the page, otherwise throws an exception
     * @param page the page
     * @param value the value to not find
     * @throws RuntimeException if string is present after three seconds
     */
    public static void waitUntilStringIsNotPresent(HtmlPage page, String value) {
        long maxWaitTime = TimeUnit.SECONDS.toMillis(3);
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            if (page.querySelector("*").getTextContent().contains(value)) {
                try {
                    Thread.sleep(100); // Adjust the interval as needed
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                System.out.println("Took '" + (System.currentTimeMillis() - startTime) + "' milliseconds " +
                        "until string '" + value + "' was no longer present");
                return;
            }
        }

        throw new RuntimeException("String '" + value + "' is still present '" + value + "' after '" + maxWaitTime + "' seconds");
    }

    /**
     * Waits until the given query selector is visible on the page, otherwise returns null
     * @param page the page
     * @param query the query selector for the element
     */
    public static HtmlElement waitUntilElementIsPresent(HtmlPage page, String query) {
        long maxWaitTime = TimeUnit.SECONDS.toMillis(3);
        long startTime = System.currentTimeMillis();

        // Loop until the element is found or timeout occurs
        HtmlElement element = null;
        while (element == null && System.currentTimeMillis() - startTime < maxWaitTime) {
            // Try to find the element
            try {
                element = page.querySelector(query);
            } catch (Exception ignored) {
                System.out.println("Looking again for element: " + query);
            }

            // If the element is not found, wait for a short interval before trying again
            if (element == null) {
                try {
                    Thread.sleep(100); // Adjust the interval as needed
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return element;
    }
}

/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package jenkins.security;

import org.acegisecurity.userdetails.UserDetails;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Mean to be included in test classes to provide a way to spy on the SecurityListener events.<br />
 *
 * <h3>Usage example:</h3>
 * <h4>Declaration</h4>
 * You need first to register a {@link org.jvnet.hudson.test.TestExtension} into Jenkins instance, like the following code:
 * <pre> {@code
@literal @TestExtension
  public static class SpySecurityListenerImpl extends SpySecurityListener {}
}</pre>

 * <h4>Reference retrieval</h4>
 * Then during the startup of the test, you can retrieve the registered instance of your class
 * <pre> {@code private SpySecurityListener spySecurityListener;

 @literal @Before
  public void prepareListeners(){
     this.spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
  }
}</pre>

 * <h4>Assert</h4>
 * And finally you can use it
     <pre> {@code makeRequestWithAuthAndVerify(null, "anonymous");
  spySecurityListener.authenticatedCalls.assertNoNewEvents();
    }</pre>
 */
public abstract class SpySecurityListener extends SecurityListener {
    public final EventQueue<UserDetails> authenticatedCalls = new EventQueue<>();
    public final EventQueue<String> failedToAuthenticateCalls = new EventQueue<>();
    public final EventQueue<String> loggedInCalls = new EventQueue<>();
    public final EventQueue<String> failedToLogInCalls = new EventQueue<>();
    public final EventQueue<String> loggedOutCalls = new EventQueue<>();

    public void clearPreviousCalls(){
        this.authenticatedCalls.clear();
        this.failedToAuthenticateCalls.clear();
        this.loggedInCalls.clear();
        this.failedToLogInCalls.clear();
        this.loggedOutCalls.clear();
    }

    public void assertAllEmpty(){
        this.authenticatedCalls.assertNoNewEvents();
        this.failedToAuthenticateCalls.assertNoNewEvents();
        this.loggedInCalls.assertNoNewEvents();
        this.failedToLogInCalls.assertNoNewEvents();
        this.loggedOutCalls.assertNoNewEvents();
    }

    @Override
    protected void authenticated(@Nonnull UserDetails details) {
        this.authenticatedCalls.add(details);
    }

    @Override
    protected void failedToAuthenticate(@Nonnull String username) {
        this.failedToAuthenticateCalls.add(username);
    }

    @Override
    protected void loggedIn(@Nonnull String username) {
        this.loggedInCalls.add(username);
    }

    @Override
    protected void failedToLogIn(@Nonnull String username) {
        this.failedToLogInCalls.add(username);
    }

    @Override
    protected void loggedOut(@Nonnull String username) {
        this.loggedOutCalls.add(username);

    }

    public static class EventQueue<T> {
        private final Deque<T> eventList = new LinkedList<>();

        private EventQueue add(T t){
            eventList.addFirst(t);
            return this;
        }

        public void assertLastEventIsAndThenRemoveIt(T expected){
            assertLastEventIsAndThenRemoveIt(expected::equals);
        }

        public void assertLastEventIsAndThenRemoveIt(Predicate<T> predicate){
            if(eventList.isEmpty()){
                fail("event list is empty");
            }

            T t = eventList.removeLast();
            assertTrue(predicate.test(t));
            eventList.clear();
        }

        public void assertNoNewEvents(){
            assertEquals("list of event should be empty", eventList.size(), 0);
        }

        public void clear(){
            eventList.clear();
        }
    }
}

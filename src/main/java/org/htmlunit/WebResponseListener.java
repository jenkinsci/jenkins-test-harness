package org.htmlunit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public interface WebResponseListener {

    void onLoadWebResponse(WebRequest webRequest, WebResponse webResponse) throws IOException;

    final class StatusListener implements WebResponseListener {

        private final int statusCode;
        private final List<WebResponse> responses = new CopyOnWriteArrayList<>();

        public StatusListener(final int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public void onLoadWebResponse(WebRequest webRequest, WebResponse webResponse) throws IOException {
            if (webResponse.getStatusCode() == statusCode) {
                responses.add(webResponse);
            }
        }

        public void assertHasResponses() {
            assertThat(responses, not(empty()));
        }

        public List<WebResponse> getResponses() {
            return responses;
        }
    }
}

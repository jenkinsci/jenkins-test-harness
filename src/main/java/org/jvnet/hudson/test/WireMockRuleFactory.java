package org.jvnet.hudson.test;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Provides a way to record and mock REST calls.  The underlying structure is 
 * <a href="http://wiremock.org/">WireMock</a>.  To use: 
 * <pre>
 * {@code
 * WireMockRuleFactory wmrf = new WireMockRuleFactory();
 * @Rule
 * WireMockRule = wmrf.getRule(8089); //port is configurable  
 * }
 * </pre>
 *
 * Point your system to "http://localhost:8089/" to see mocked responses.
 *
 * To record mocks: {@code mvn test -wiremock.record="http://api_url"}  Clearing 
 * previously recorded mocks before generating new ones is recommended. 
 */
public class WireMockRuleFactory {
    private String urlToMock = System.getProperty("wiremock.record");

    public WireMockRule getRule(int port) {
        return getRule(wireMockConfig().port(port));
    }

    public WireMockRule getRule(Options options) {
        if(urlToMock != null && !urlToMock.isEmpty()) {
            return new WireMockRecorderRule(options, urlToMock);
        } else {
            return new WireMockRule(options);
        }
    }


    private class WireMockRecorderRule extends WireMockRule {
        //needed for WireMockRule file location
        private String mappingLocation = "src/test/resources";

        public WireMockRecorderRule(Options options, String url) {
            super(options);
            this.stubFor(get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom(url)));
            this.enableRecordMappings(new SingleRootFileSource(mappingLocation + "/mappings"), new SingleRootFileSource(mappingLocation + "/__files"));
        }
    }
}
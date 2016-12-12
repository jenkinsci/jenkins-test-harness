package org.jvnet.hudson.test;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Provides a way to record and mock REST calls. The underlying structure is 
 * <a href="http://wiremock.org/">WireMock</a>. To use: 
 * <pre>
 * {@code
 * WireMockRuleFactory wmrf = new WireMockRuleFactory();
 * @Rule
 * WireMockRule wireMockRule = wmrf.getRule(8089); //port is configurable  
 * }
 * </pre>
 *
 * Point your system to "http://localhost:8089/" to see mocked responses.
 *
 * To record mocks: {@code mvn test -Dwiremock.record="http://api_url"} Clearing 
 * previously recorded mocks before generating new ones is recommended. 
 *
 * The mocks are defaultly written to `src/test/resources`. Use the 2nd 
 * constructor option to set an alternative location:
 * <pre>
 * {@code
 * WireMockRuleFactory wmrf = new WireMockRuleFactory();
 * @Rule
 * WireMockRule wireMockRule = wmrf.getRule("src/test/resources/mockLoc")
 * }
 * </pre>
 */
public class WireMockRuleFactory {
    private String urlToMock = System.getProperty("wiremock.record");

    public WireMockRule getRule(int port) {
        return getRule(WireMockConfiguration.wireMockConfig().port(port));
    }

    public WireMockRule getRule(WireMockConfiguration options) {
        if(urlToMock != null && !urlToMock.isEmpty()) {
            return new WireMockRecorderRule(options, urlToMock);
        } else {
            return new WireMockRule(options);
        }
    }

    public WireMockRule getRule(String mapLoc) {
        return getRule(mapLoc, WireMockConfiguration.wireMockConfig());
    }

    public WireMockRule getRule(String mapLoc, WireMockConfiguration options) {
        if(urlToMock != null && !urlToMock.isEmpty()) {
            return new WireMockRecorderRule(options, urlToMock, mapLoc);
        } else {
            return new WireMockRule(options
               .usingFilesUnderClasspath(mapLoc));
        }
    }


    private class WireMockRecorderRule extends WireMockRule {
        private String mappingLocation = "src/test/resources";

        public WireMockRecorderRule(WireMockConfiguration options, String url) {
            super(options);
            finishWireMockRuleSetup(options, url, this.mappingLocation);
        }

        public WireMockRecorderRule(WireMockConfiguration options, String url, String mappingLocation) {
            super(options);
            finishWireMockRuleSetup(options, url, mappingLocation);
        }

        private void finishWireMockRuleSetup(WireMockConfiguration options, String url, String mappingLocation) {
            this.stubFor(get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom(url)));
            this.enableRecordMappings(new SingleRootFileSource(mappingLocation + "/mappings"), new SingleRootFileSource(mappingLocation + "/__files"));
        }
    }
}
/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.circuitbreaker;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.rxjava3.core.http.HttpClient;
import org.junit.jupiter.api.Test;

/**
 * A call killed by the gateway request timeout never completes: RxJava disposes the chain instead of terminating it.
 * The circuit breaker only sees a cancellation, without its cause.
 */
@GatewayTest
class CircuitBreakerInterruptedCallsIntegrationTest extends AbstractIntegrationTest {

    private static final int REQUEST_TIMEOUT = 500;
    private static final int BACKEND_DELAY = 5000;

    @Override
    public void configureGateway(GatewayConfigurationBuilder configurationBuilder) {
        configurationBuilder.set("http.requestTimeout", REQUEST_TIMEOUT);
    }

    @Test
    @DeployApi("/apis/v4/circuit-breaker-interrupted.json")
    void should_open_circuit_when_an_interrupted_call_lasted_longer_than_the_slow_call_threshold(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend").withFixedDelay(BACKEND_DELAY)));

        assertCallReturns(client, "/circuit-breaker-interrupted", HttpStatusCode.GATEWAY_TIMEOUT_504);
        assertCallReturns(client, "/circuit-breaker-interrupted", HttpStatusCode.SERVICE_UNAVAILABLE_503);

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi("/apis/v4/circuit-breaker-interrupted-short.json")
    void should_ignore_an_interrupted_call_shorter_than_the_slow_call_threshold(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend").withFixedDelay(BACKEND_DELAY)));

        assertCallReturns(client, "/circuit-breaker-interrupted-short", HttpStatusCode.GATEWAY_TIMEOUT_504);
        assertCallReturns(client, "/circuit-breaker-interrupted-short", HttpStatusCode.GATEWAY_TIMEOUT_504);

        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi("/apis/v4/circuit-breaker-interrupted-disabled.json")
    void should_ignore_interrupted_calls_when_the_option_is_disabled(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend").withFixedDelay(BACKEND_DELAY)));

        assertCallReturns(client, "/circuit-breaker-interrupted-disabled", HttpStatusCode.GATEWAY_TIMEOUT_504);
        assertCallReturns(client, "/circuit-breaker-interrupted-disabled", HttpStatusCode.GATEWAY_TIMEOUT_504);

        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/endpoint")));
    }
}

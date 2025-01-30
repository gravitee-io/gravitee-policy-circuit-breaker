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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Circuit breaker internal behavior is based on a registry with {@link io.gravitee.gateway.api.ExecutionContext#ATTR_RESOLVED_PATH} as key.
 *
 * This means that the key is the path of the resolved flow for the call.
 * To avoid flakiness when running tests, it is preferable to deploy the API once per test, to avoid having one and only one instance of the policy and so, to avoid mixing circuit breaking statistics across tests.
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
class CircuitBreakerPolicyIntegrationV3Test extends V3EngineTest {

    @Test
    @DeployApi("/apis/v2/circuit-breaker.json")
    void should_open_circuit_when_too_many_slow_calls(HttpClient client) {
        wiremock.stubFor(get("/endpoint/my_team").willReturn(ok("response from backend").withFixedDelay(750)));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker/my_team")
            .flatMap(HttpClientRequest::rxSend)
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint/my_team").willReturn(ok("response from backend").withFixedDelay(750)));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker/my_team")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.SERVICE_UNAVAILABLE_503);
                assertThat(response.statusMessage()).isEqualToIgnoringCase("Service Unavailable");
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(responseBody -> {
                assertThat(responseBody).hasToString(CircuitBreakerPolicy.CIRCUIT_BREAKER_OPEN_STATE_MESSAGE);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint/my_team")));
    }

    @Test
    @DeployApi("/apis/v2/circuit-breaker.json")
    void should_open_circuit_when_too_many_failures(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(505);
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(responseBody -> {
                assertThat(responseBody).hasToString("response from backend");
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.SERVICE_UNAVAILABLE_503);
                assertThat(response.statusMessage()).isEqualToIgnoringCase("Service Unavailable");
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(responseBody -> {
                assertThat(responseBody).hasToString(CircuitBreakerPolicy.CIRCUIT_BREAKER_OPEN_STATE_MESSAGE);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi("/apis/v2/circuit-breaker-redirect.json")
    void should_redirect_to_url_when_circuit_opens(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker-redirect")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(505);
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(responseBody -> {
                assertThat(responseBody).hasToString("response from backend");
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));
        redirectServer.stubFor(get("/redirection").willReturn(ok("redirection went well")));

        client
            .rxRequest(HttpMethod.GET, "/v2-circuit-breaker-redirect")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.toFlowable();
            })
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(responseBody -> {
                assertThat(responseBody).hasToString("redirection went well");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
        redirectServer.verify(1, getRequestedFor(urlPathEqualTo("/redirection")));
    }
}

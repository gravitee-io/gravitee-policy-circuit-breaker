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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.definition.model.Api;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import io.reactivex.observers.TestObserver;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Circuit breaker internal behavior is based on a registry with {@link io.gravitee.gateway.api.ExecutionContext#ATTR_RESOLVED_PATH} as key.
 *
 * This means that the key is the path of the resolved flow for the call.
 * To avoid flakiness when running tests, it is preferable to deploy the API once per test, to avoid having one and only one instance of the policy and so, to avoid mixing circuit breaking statistics across tests.
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
class CircuitBreakerPolicyIntegrationTest extends AbstractPolicyTest<CircuitBreakerPolicy, CircuitBreakerPolicyConfiguration> {

    public static final String LOCALHOST = "localhost:";
    public static final String REDIRECT_URL = LOCALHOST + "8089";

    @RegisterExtension
    static WireMockExtension redirectServer = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Override
    protected void configureWireMock(WireMockConfiguration configuration) {
        configuration.extensions(new ResponseTemplateTransformer(true));
    }

    @Override
    public void configureApi(Api api) {
        if (api.getId().equals("my-api-redirect")) {
            api
                .getFlows()
                .forEach(flow -> {
                    flow
                        .getPre()
                        .stream()
                        .filter(step -> policyName().equals(step.getPolicy()))
                        .forEach(step ->
                            step.setConfiguration(step.getConfiguration().replace(REDIRECT_URL, LOCALHOST + redirectServer.getPort()))
                        );
                });
        }
    }

    @Test
    @DeployApi("/apis/circuit-breaker.json")
    @DisplayName("Should open circuit when too many slow calls")
    void shouldOpenCircuitWhenSlowCalls(WebClient client) {
        wiremock.stubFor(get("/endpoint/my_team").willReturn(ok("response from backend").withFixedDelay(750)));

        TestObserver<HttpResponse<Buffer>> obs = client.get("/test/my_team").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint/my_team").willReturn(ok("response from backend").withFixedDelay(750)));

        obs = client.get("/test/my_team").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.SERVICE_UNAVAILABLE_503);
                assertThat(response.statusMessage()).isEqualToIgnoringCase("Service Unavailable");
                assertThat(response.bodyAsString()).isEqualTo(CircuitBreakerPolicy.CIRCUIT_BREAKER_OPEN_STATE_MESSAGE);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint/my_team")));
    }

    @Test
    @DeployApi("/apis/circuit-breaker.json")
    @DisplayName("Should open circuit when too many failures")
    void shouldOpenCircuitWhenFailures(WebClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        TestObserver<HttpResponse<Buffer>> obs = client.get("/test").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(505);
                assertThat(response.bodyAsString()).isEqualTo("response from backend");
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        obs = client.get("/test").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.SERVICE_UNAVAILABLE_503);
                assertThat(response.statusMessage()).isEqualToIgnoringCase("Service Unavailable");
                assertThat(response.bodyAsString()).isEqualTo(CircuitBreakerPolicy.CIRCUIT_BREAKER_OPEN_STATE_MESSAGE);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi("/apis/circuit-breaker-redirect.json")
    @DisplayName("Should redirect to URL if opened circuit due to too many failures")
    void shouldRedirectWhenCircuitOpen(WebClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));

        TestObserver<HttpResponse<Buffer>> obs = client.get("/circuit-redirect").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(505);
                assertThat(response.bodyAsString()).isEqualTo("response from backend");
                return true;
            })
            .assertNoErrors();

        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(505).withBody("response from backend")));
        redirectServer.stubFor(get("/redirection").willReturn(ok("redirection went well")));

        obs = client.get("/circuit-redirect").rxSend().test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
                assertThat(response.bodyAsString()).isEqualTo("redirection went well");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
        redirectServer.verify(1, getRequestedFor(urlPathEqualTo("/redirection")));
    }
}

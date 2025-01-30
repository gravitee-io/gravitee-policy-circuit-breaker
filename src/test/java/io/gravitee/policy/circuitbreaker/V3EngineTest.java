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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@GatewayTest(v2ExecutionMode = ExecutionMode.V3)
public class V3EngineTest extends AbstractPolicyTest<CircuitBreakerPolicy, CircuitBreakerPolicyConfiguration> {

    public static final String LOCALHOST = "localhost:";
    public static final String REDIRECT_URL = LOCALHOST + "8089";

    @RegisterExtension
    static WireMockExtension redirectServer = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Override
    public void configureApi(Api api) {
        if (api.getId().endsWith("-redirect")) {
            api
                .getFlows()
                .forEach(flow ->
                    flow
                        .getPre()
                        .stream()
                        .filter(step -> policyName().equals(step.getPolicy()))
                        .forEach(step ->
                            step.setConfiguration(step.getConfiguration().replace(REDIRECT_URL, LOCALHOST + redirectServer.getPort()))
                        )
                );
        }
    }
}

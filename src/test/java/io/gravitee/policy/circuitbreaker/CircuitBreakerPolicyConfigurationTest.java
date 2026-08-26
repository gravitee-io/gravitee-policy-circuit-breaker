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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerPolicyConfigurationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void should_apply_schema_defaults_when_configuration_is_empty() throws IOException {
        var configuration = MAPPER.readValue("{}", CircuitBreakerPolicyConfiguration.class);
        var actual = MAPPER.convertValue(configuration, new TypeReference<Map<String, Object>>() {});

        assertThat(schemaDefaults())
            .isNotEmpty()
            .allSatisfy((property, expected) ->
                assertThat(actual.get(property))
                    .as("default of %s", property)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Number.class))
                    .extracting(Number::doubleValue)
                    .isEqualTo(expected)
            );
    }

    @Test
    void should_build_a_valid_circuit_breaker_config_from_an_empty_configuration() throws IOException {
        var policy = new CircuitBreakerPolicyV3(MAPPER.readValue("{}", CircuitBreakerPolicyConfiguration.class));

        assertThatCode(policy::circuitBreakerConfig).doesNotThrowAnyException();

        var config = policy.circuitBreakerConfig();
        assertThat(config.getFailureRateThreshold()).isEqualTo(50);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(100);
        assertThat(config.getSlowCallDurationThreshold()).hasMillis(1000);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(1000L);
        assertThat(config.getSlidingWindowSize()).isEqualTo(100);
    }

    /**
     * Reads the defaults advertised by the console form, so that the Java defaults can never drift from them.
     */
    private static Map<String, Double> schemaDefaults() throws IOException {
        try (InputStream schema = CircuitBreakerPolicyConfigurationTest.class.getResourceAsStream("/schemas/schema-form.json")) {
            Map<String, Map<String, Object>> properties = MAPPER.convertValue(
                MAPPER.readTree(schema).get("properties"),
                new TypeReference<Map<String, Map<String, Object>>>() {}
            );

            var defaults = new LinkedHashMap<String, Double>();
            properties.forEach((name, definition) -> {
                if (definition.get("default") instanceof Number value) {
                    defaults.put(name, value.doubleValue());
                }
            });
            return defaults;
        }
    }
}

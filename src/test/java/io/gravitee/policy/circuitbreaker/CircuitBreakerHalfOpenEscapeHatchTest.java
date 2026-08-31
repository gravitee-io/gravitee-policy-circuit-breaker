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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * A permission is acquired when the policy runs, but recorded only when the invoker completes. A request that acquires
 * one and never reaches the invoker — a later request policy interrupting the chain, another policy overriding the
 * invoker — never gives it back. In half-open state that permission is one of the probes the circuit breaker is waiting
 * for, so the circuit stays half-open and answers every subsequent call with a 503.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerHalfOpenEscapeHatchTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void should_stay_half_open_forever_when_a_probe_is_never_recorded() throws InterruptedException {
        var circuitBreaker = halfOpenCircuitBreakerWithAnUnrecordedProbe(new CircuitBreakerPolicyConfiguration());

        assertThat(awaitStateOtherThan(circuitBreaker, CircuitBreaker.State.HALF_OPEN, Duration.ofMillis(500))).isEqualTo(
            CircuitBreaker.State.HALF_OPEN
        );
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void should_leave_half_open_state_when_a_maximum_wait_duration_is_configured() throws InterruptedException {
        var configuration = new CircuitBreakerPolicyConfiguration();
        configuration.setMaxWaitDurationInHalfOpenState(100);

        var circuitBreaker = halfOpenCircuitBreakerWithAnUnrecordedProbe(configuration);

        assertThat(awaitStateOtherThan(circuitBreaker, CircuitBreaker.State.HALF_OPEN, TIMEOUT)).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private static CircuitBreaker halfOpenCircuitBreakerWithAnUnrecordedProbe(CircuitBreakerPolicyConfiguration configuration) {
        var circuitBreaker = CircuitBreaker.of("test", new CircuitBreakerPolicyV3(configuration).circuitBreakerConfig());

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.tryAcquirePermission()).as("the probe permission is acquired").isTrue();

        return circuitBreaker;
    }

    private static CircuitBreaker.State awaitStateOtherThan(CircuitBreaker circuitBreaker, CircuitBreaker.State state, Duration timeout)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (circuitBreaker.getState() == state && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return circuitBreaker.getState();
    }
}

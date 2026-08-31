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
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerPolicyTest {

    private static final int CONCURRENT_REQUESTS = 16;

    private final CircuitBreakerPolicy policy = new CircuitBreakerPolicy(new CircuitBreakerPolicyConfiguration());

    @Test
    void should_hold_one_circuit_per_key() {
        assertThat(policy.circuitBreaker("an-api")).isSameAs(policy.circuitBreaker("an-api"));
        assertThat(policy.circuitBreaker("an-api")).isNotSameAs(policy.circuitBreaker("another-api"));
    }

    /**
     * Two requests reaching the policy at the same time must record their outcome in the same circuit: a circuit built
     * per request would never accumulate enough calls to open.
     */
    @Test
    void should_hand_the_same_circuit_to_concurrent_requests() throws Exception {
        var barrier = new CyclicBarrier(CONCURRENT_REQUESTS);

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<CircuitBreaker>> circuits = IntStream.range(0, CONCURRENT_REQUESTS)
                .mapToObj(request ->
                    executor.submit(() -> {
                        barrier.await();
                        return policy.circuitBreaker("an-api");
                    })
                )
                .toList();

            var expected = circuits.get(0).get();
            for (var circuit : circuits) {
                assertThat(circuit.get()).isSameAs(expected);
            }
        }
    }
}

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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.WriteStream;
import io.gravitee.reporter.api.http.Metrics;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerPolicyV3Test {

    /**
     * Gateways 4.12.15+ no longer prime the metrics timing field before invoking the backend, so the policy cannot
     * rely on it as a start time: a fast successful call must not be recorded as slow.
     */
    @Test
    void should_not_record_a_fast_call_as_slow_when_the_metrics_field_is_not_primed() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
            "an-api",
            CircuitBreakerConfig.custom().slowCallDurationThreshold(Duration.ofMillis(4500)).minimumNumberOfCalls(1).build()
        );

        ExecutionContext context = mock(ExecutionContext.class, RETURNS_DEEP_STUBS);
        when(context.request().metrics()).thenReturn(Metrics.on(System.currentTimeMillis()).build());

        ProxyResponse proxyResponse = mock(ProxyResponse.class);
        when(proxyResponse.status()).thenReturn(200);

        Invoker backend = (ctx, stream, connectionHandler) ->
            connectionHandler.handle(
                new ProxyConnection() {
                    @Override
                    public WriteStream<Buffer> write(Buffer buffer) {
                        return this;
                    }

                    @Override
                    public void end() {}

                    @Override
                    public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
                        responseHandler.handle(proxyResponse);
                        return this;
                    }
                }
            );

        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        new CircuitBreakerPolicyV3.CircuitBreakerInvoker(backend, circuitBreaker).invoke(context, null, connection ->
            connection.responseHandler(response -> {})
        );

        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isZero();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}

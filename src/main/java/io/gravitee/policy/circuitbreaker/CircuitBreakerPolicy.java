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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.InternalContextAttributes;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.invoker.HttpInvoker;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CircuitBreakerPolicy extends CircuitBreakerPolicyV3 implements HttpPolicy {

    private CircuitBreaker circuitBreaker;

    public CircuitBreakerPolicy(CircuitBreakerPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String id() {
        return "policy-circuit-breaker";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return Completable.defer(() -> {
            var circuitBreaker = get(ctx);

            if (circuitBreaker.tryAcquirePermission()) {
                HttpInvoker defaultInvoker = ctx.getInternalAttribute(InternalContextAttributes.ATTR_INTERNAL_INVOKER);
                var circuitBreakerInvoker = new CircuitBreakerInvoker(defaultInvoker, circuitBreaker);
                ctx.setInternalAttribute(InternalContextAttributes.ATTR_INTERNAL_INVOKER, circuitBreakerInvoker);
                return Completable.complete();
            }

            if (configuration.getRedirectToURL() != null && !configuration.getRedirectToURL().isEmpty()) {
                var endpoint = ctx.getTemplateEngine().evalNow(configuration.getRedirectToURL(), String.class);
                ctx.setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, endpoint);
                return Completable.complete();
            }

            return ctx.interruptWith(
                new ExecutionFailure(503)
                    .key(CIRCUIT_BREAKER_OPEN_STATE)
                    .message(CIRCUIT_BREAKER_OPEN_STATE_MESSAGE)
                    .parameters(
                        Map.ofEntries(
                            Map.entry("failure_rate", circuitBreaker.getMetrics().getFailureRate()),
                            Map.entry("slow_call_rate", circuitBreaker.getMetrics().getSlowCallRate())
                        )
                    )
            );
        });
    }

    private CircuitBreaker get(HttpPlainExecutionContext ctx) {
        if (circuitBreaker == null) {
            String apiId = ctx.getAttribute(ExecutionContext.ATTR_API);
            circuitBreaker =
                CircuitBreaker.of(
                    apiId,
                    CircuitBreakerConfig
                        .custom()
                        .failureRateThreshold(configuration.getFailureRateThreshold())
                        .slowCallRateThreshold(configuration.getSlowCallRateThreshold())
                        .slowCallDurationThreshold(Duration.ofMillis(configuration.getSlowCallDurationThreshold()))
                        .waitDurationInOpenState(Duration.ofMillis(configuration.getWaitDurationInOpenState()))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .minimumNumberOfCalls(1)
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(configuration.getWindowSize())
                        .build()
                );
        }
        return circuitBreaker;
    }

    static class CircuitBreakerInvoker implements HttpInvoker {

        private final HttpInvoker delegate;
        private final CircuitBreaker circuitBreaker;

        public CircuitBreakerInvoker(HttpInvoker delegate, CircuitBreaker circuitBreaker) {
            this.delegate = delegate;
            this.circuitBreaker = circuitBreaker;
        }

        @Override
        public String getId() {
            return "circuit-breaker-invoker";
        }

        @Override
        public Completable invoke(HttpExecutionContext ctx) {
            return delegate
                .invoke(ctx)
                .compose(upstream ->
                    upstream
                        .doOnComplete(() -> {
                            long elapsedTime = System.currentTimeMillis() - ctx.metrics().getEndpointResponseTimeMs();
                            int responseStatus = ctx.response().status();

                            log.debug("Hook on Complete Elapsed time: {} ms", elapsedTime);

                            if (responseStatus >= HttpStatusCode.INTERNAL_SERVER_ERROR_500) {
                                circuitBreaker.onError(elapsedTime, TimeUnit.MILLISECONDS, null);
                            } else {
                                circuitBreaker.onSuccess(elapsedTime, TimeUnit.MILLISECONDS);
                            }
                        })
                        .doOnError(th -> {
                            long elapsedTime = System.currentTimeMillis() - ctx.metrics().getEndpointResponseTimeMs();
                            log.debug("Hook on Error Elapsed time: {} ms", elapsedTime);
                            circuitBreaker.onError(elapsedTime, TimeUnit.MILLISECONDS, th);
                        })
                        .doOnDispose(circuitBreaker::releasePermission)
                );
        }
    }
}

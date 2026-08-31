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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;

@CustomLog
public class CircuitBreakerPolicy extends CircuitBreakerPolicyV3 implements HttpPolicy {

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
                var circuitBreakerInvoker = new CircuitBreakerInvoker(defaultInvoker, circuitBreaker, configuration);
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
        return circuitBreaker(ctx.getAttribute(ExecutionContext.ATTR_API));
    }

    static class CircuitBreakerInvoker implements HttpInvoker {

        private final HttpInvoker delegate;
        private final CircuitBreaker circuitBreaker;
        private final CircuitBreakerPolicyConfiguration configuration;

        public CircuitBreakerInvoker(HttpInvoker delegate, CircuitBreaker circuitBreaker, CircuitBreakerPolicyConfiguration configuration) {
            this.delegate = delegate;
            this.circuitBreaker = circuitBreaker;
            this.configuration = configuration;
        }

        @Override
        public String getId() {
            return "circuit-breaker-invoker";
        }

        /**
         * A disposed chain does not say why it was cancelled: a gateway request timeout and a client giving up look
         * the same. Only interruptions longer than the configured slow call threshold are recorded, since beyond it the
         * call is already considered abnormal.
         */
        private boolean isInterruptionWorthRecording(long elapsedTimeNanos) {
            return (
                configuration.isRecordInterruptedCallsAsFailures() &&
                elapsedTimeNanos > TimeUnit.MILLISECONDS.toNanos(configuration.getSlowCallDurationThreshold())
            );
        }

        @Override
        public Completable invoke(HttpExecutionContext ctx) {
            return Completable.defer(() -> {
                long startTime = System.nanoTime();

                return delegate
                    .invoke(ctx)
                    .doOnComplete(() -> {
                        long elapsedTimeNanos = System.nanoTime() - startTime;
                        int responseStatus = ctx.response().status();

                        ctx.withLogger(log).debug("Hook on Complete Elapsed time: {} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));

                        if (responseStatus >= HttpStatusCode.INTERNAL_SERVER_ERROR_500) {
                            circuitBreaker.onError(elapsedTimeNanos, TimeUnit.NANOSECONDS, null);
                        } else {
                            circuitBreaker.onSuccess(elapsedTimeNanos, TimeUnit.NANOSECONDS);
                        }
                    })
                    .doOnError(th -> {
                        long elapsedTimeNanos = System.nanoTime() - startTime;
                        ctx.withLogger(log).debug("Hook on Error Elapsed time: {} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));
                        circuitBreaker.onError(elapsedTimeNanos, TimeUnit.NANOSECONDS, th);
                    })
                    .doOnDispose(() -> {
                        long elapsedTimeNanos = System.nanoTime() - startTime;
                        ctx.withLogger(log).debug("Hook on Dispose Elapsed time: {} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));

                        if (isInterruptionWorthRecording(elapsedTimeNanos)) {
                            circuitBreaker.onError(elapsedTimeNanos, TimeUnit.NANOSECONDS, null);
                        } else {
                            circuitBreaker.releasePermission();
                        }
                    });
            });
        }
    }
}

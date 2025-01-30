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
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.gateway.api.stream.WriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.circuitbreaker.configuration.CircuitBreakerPolicyConfiguration;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CircuitBreakerPolicy {

    private static final String CIRCUIT_BREAKER_OPEN_STATE = "CIRCUIT_BREAKER_OPEN_STATE";
    static final String CIRCUIT_BREAKER_OPEN_STATE_MESSAGE = "Service temporarily unavailable";

    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    private final CircuitBreakerPolicyConfiguration configuration;

    public CircuitBreakerPolicy(CircuitBreakerPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        CircuitBreaker circuitBreaker = get(executionContext);
        boolean permission = circuitBreaker.tryAcquirePermission();

        if (permission) {
            Invoker defaultInvoker = (Invoker) executionContext.getAttribute(ExecutionContext.ATTR_INVOKER);
            executionContext.setAttribute(ExecutionContext.ATTR_INVOKER, new CircuitBreakerInvoker(defaultInvoker, circuitBreaker));

            policyChain.doNext(request, response);
        } else {
            if (configuration.getRedirectToURL() != null && !configuration.getRedirectToURL().isEmpty()) {
                String endpoint = configuration.getRedirectToURL();
                endpoint = executionContext.getTemplateEngine().getValue(endpoint, String.class);
                executionContext.setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, endpoint);
                policyChain.doNext(request, response);
            } else {
                policyChain.failWith(
                    PolicyResult.failure(
                        CIRCUIT_BREAKER_OPEN_STATE,
                        HttpStatusCode.SERVICE_UNAVAILABLE_503,
                        CIRCUIT_BREAKER_OPEN_STATE_MESSAGE,
                        Maps
                            .<String, Object>builder()
                            .put("failure_rate", circuitBreaker.getMetrics().getFailureRate())
                            .put("slow_call_rate", circuitBreaker.getMetrics().getSlowCallRate())
                            .build()
                    )
                );
            }
        }
    }

    private CircuitBreaker get(ExecutionContext context) {
        String resolvedPath = (String) context.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH);

        return registry.circuitBreaker(
            resolvedPath,
            () ->
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

    static class CircuitBreakerInvoker implements Invoker {

        private final Invoker decorated;
        private final CircuitBreaker circuitBreaker;

        CircuitBreakerInvoker(Invoker decorated, CircuitBreaker circuitBreaker) {
            this.decorated = decorated;
            this.circuitBreaker = circuitBreaker;
        }

        @Override
        public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
            decorated.invoke(
                context,
                stream,
                proxyConnection -> {
                    ProxyConnection wrappedProxyConnection = new ProxyConnection() {
                        @Override
                        public WriteStream<Buffer> write(Buffer buffer) {
                            proxyConnection.write(buffer);
                            return this;
                        }

                        @Override
                        public void end() {
                            proxyConnection.end();
                        }

                        @Override
                        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
                            return proxyConnection.responseHandler(
                                new CircuitBreakerResponseHandler(responseHandler, context, circuitBreaker)
                            );
                        }
                    };

                    connectionHandler.handle(wrappedProxyConnection);
                }
            );
        }
    }

    static class CircuitBreakerResponseHandler implements Handler<ProxyResponse> {

        private final Handler<ProxyResponse> responseHandler;
        private final ExecutionContext context;
        private final CircuitBreaker circuitBreaker;

        CircuitBreakerResponseHandler(Handler<ProxyResponse> responseHandler, ExecutionContext context, CircuitBreaker circuitBreaker) {
            this.responseHandler = responseHandler;
            this.context = context;
            this.circuitBreaker = circuitBreaker;
        }

        @Override
        public void handle(ProxyResponse proxyResponse) {
            long elapsedTime = System.currentTimeMillis() - context.request().metrics().getApiResponseTimeMs();
            int status = proxyResponse.status();

            if (status >= HttpStatusCode.INTERNAL_SERVER_ERROR_500) {
                circuitBreaker.onError(elapsedTime, TimeUnit.MILLISECONDS, null);
            } else {
                circuitBreaker.onSuccess(elapsedTime, TimeUnit.MILLISECONDS);
            }

            responseHandler.handle(proxyResponse);
        }
    }
}

## Overview
Switch to another backend, or fail the call with a `503 Service Unavailable`, when the target starts returning errors or
answering slowly. Error and latency thresholds are configurable, so the policy stops sending traffic to a target it
detects as failing and keeps the rest of the system responsive.

The implementation is based on [Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker).

## How the circuit opens

Call outcomes are recorded in a count-based sliding window of `windowSize` calls. The failure and slow call rates are
only computed once `minimumNumberOfCalls` calls have been recorded, so the circuit cannot open on a single unlucky call
unless you ask for it. A value of `minimumNumberOfCalls` greater than `windowSize` is silently capped to `windowSize`.

A call counts as a failure when the backend answers with a status code greater than or equal to 500, and as a slow call
when it takes longer than `slowCallDurationThreshold`. A slow failing call counts in both rates.

Once open, the circuit stays open for `waitDurationInOpenState`, then lets `permittedNumberOfCallsInHalfOpenState` calls
through. It closes again only once all of them have been recorded and the rates are back below their thresholds. Beware
that the half-open decision uses `min(minimumNumberOfCalls, permittedNumberOfCallsInHalfOpenState)` as its minimum:
raising the number of permitted calls without raising `minimumNumberOfCalls` changes nothing.

## Interrupted calls

A call cancelled before the backend answered — a gateway request timeout, a client giving up — is ignored by default:
it is counted neither as a success nor as a failure. The shorter the gateway `http.requestTimeout`, the fewer slow
calls the circuit breaker gets to see.

Set `recordInterruptedCallsAsFailures` to `true` to record such interruptions as failures, provided they lasted longer
than `slowCallDurationThreshold`. Below that threshold they stay ignored, which keeps quick client abandonments out of
the statistics. This option only applies to the reactive engine.

## Where the state lives

The state of a circuit is held in memory, per gateway instance and per policy instance. It is never shared between
gateway nodes, and it is reset whenever the API is redeployed or the gateway restarts. A circuit reported as open on
one node may well be closed on another.




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key| Parameters |
| --- | ---  |
| CIRCUIT_BREAKER_OPEN_STATE| failure_rate, slow_call_rate |



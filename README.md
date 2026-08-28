
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# Circuit Breaker

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-policy-circuit-breaker/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-policy-circuit-breaker/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-policy-circuit-breaker/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-policy-circuit-breaker.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-policy-circuit-breaker)

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

By default the circuit breaker waits indefinitely for those permitted calls to be recorded. A call that is allowed
through but never reaches the backend — a later policy interrupting the request, another policy replacing the invoker —
is never recorded, so the circuit stays half-open and answers every subsequent call with a `503` until the API is
redeployed. Set `maxWaitDurationInHalfOpenState` to a non-zero duration to bound that wait: once it elapses the circuit
switches back to open on its own, and a new open then half-open cycle starts.

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



## Phases
The `policy-circuit-breaker` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`

### Supported flow phases:

* Request

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM |
| --- | ---  |
|~~1.x~~|~~4.5 and earlier~~ |
|2.x|4.6 and above |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Failure rate threshold<br>`failureRateThreshold`| integer<br>`[0, 100]`| ✅| `50`| Failure (status code >= 500) rate threshold before the circuit breaker switches to the open state.|
| Maximum wait duration in half-open state (in millis)<br>`maxWaitDurationInHalfOpenState`| integer<br>`[0, +Inf]`|  | `0`| The longest the circuit breaker may stay half-open before switching back to open on its own. Zero, the default, means it waits indefinitely for the permitted calls to be recorded. Set it to a non-zero value to guarantee the circuit always leaves the half-open state, even if a permitted call is never recorded.|
| Minimum number of calls<br>`minimumNumberOfCalls`| integer<br>`[1, +Inf]`|  | `1`| The minimum number of calls required, per sliding window period, before the circuit breaker can compute the error and slow call rates. A value greater than the sliding window size is capped to that size.|
| Permitted number of calls in half-open state<br>`permittedNumberOfCallsInHalfOpenState`| integer<br>`[1, +Inf]`|  | `1`| The number of calls allowed through while the circuit breaker is half-open. The circuit closes again only once they have all been recorded, so a single failing call no longer condemns the circuit for a whole cycle.|
| Record interrupted calls as failures<br>`recordInterruptedCallsAsFailures`| boolean|  | | Record a call interrupted before the backend answered — a gateway request timeout for instance — as a failure, provided it lasted longer than the slow call duration threshold. Shorter interruptions, typically a client giving up, stay ignored.|
| Redirect call to URL<br>`redirectToURL`| string|  | | Redirect the call to the given URL instead of returning a '503 Service Unavailable' status (supports EL).|
| Slow call duration threshold (in millis)<br>`slowCallDurationThreshold`| integer<br>`[1, +Inf]`| ✅| `1000`| The duration threshold above which calls are considered as slow and increase the slow calls percentage.|
| Slow call rate threshold<br>`slowCallRateThreshold`| integer<br>`[0, 100]`| ✅| `100`| Slow call (response-time > slowCallDurationThreshold) rate threshold before the circuit breaker switches to the open state.|
| Wait duration in open state (in millis)<br>`waitDurationInOpenState`| integer<br>`[1, +Inf]`|  | `1000`| A fixed wait duration which controls how long the circuit breaker should stay open, before it switches to half open.|
| Sliding windows size<br>`windowSize`| integer<br>`[1, +Inf]`| ✅| `100`| The size of the sliding window which is used to record the outcome of calls when the circuit breaker is closed.|




## Examples

*Open the circuit once half of the last ten calls are slow or failing*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "Circuit Breaker example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "Circuit Breaker",
            "enabled": true,
            "policy": "policy-circuit-breaker",
            "configuration":
              {
                  "failureRateThreshold": 50,
                  "slowCallRateThreshold": 50,
                  "slowCallDurationThreshold": 500,
                  "windowSize": 10,
                  "minimumNumberOfCalls": 10,
                  "waitDurationInOpenState": 10000,
                  "permittedNumberOfCallsInHalfOpenState": 3
              }
          }
        ]
      }
    ]
  }
}

```
*Redirect to a fallback backend instead of returning a 503*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "Circuit Breaker example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "Circuit Breaker",
            "enabled": true,
            "policy": "policy-circuit-breaker",
            "configuration":
              {
                  "failureRateThreshold": 50,
                  "slowCallRateThreshold": 50,
                  "slowCallDurationThreshold": 500,
                  "windowSize": 10,
                  "minimumNumberOfCalls": 10,
                  "waitDurationInOpenState": 10000,
                  "permittedNumberOfCallsInHalfOpenState": 3,
                  "redirectToURL": "https://fallback.example.com/api"
              }
          }
        ]
      }
    ]
  }
}

```
*Take calls killed by the gateway request timeout into account*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "Circuit Breaker example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "Circuit Breaker",
            "enabled": true,
            "policy": "policy-circuit-breaker",
            "configuration":
              {
                  "failureRateThreshold": 50,
                  "slowCallRateThreshold": 50,
                  "slowCallDurationThreshold": 5000,
                  "windowSize": 10,
                  "minimumNumberOfCalls": 10,
                  "waitDurationInOpenState": 10000,
                  "permittedNumberOfCallsInHalfOpenState": 3,
                  "recordInterruptedCallsAsFailures": true
              }
          }
        ]
      }
    ]
  }
}

```


## Changelog

### [2.0.0](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.5...2.0.0) (2025-02-13)


##### chore

* **deps:** bump gravitee-parent to 22.2.4 ([518d7df](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/518d7dfa4d7d052781dd77f93350706839230ce7))


##### Features

* support reactive engine ([d7306bb](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/d7306bb8e45c56e73346f010ae76a8d4e3c1a9b5))


##### BREAKING CHANGES

* **deps:** require JDK 17

#### [1.1.5](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.4...1.1.5) (2023-07-20)


##### Bug Fixes

* update policy description ([8d6273f](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/8d6273f180d14412a295986f1193420e9e441a01))

#### [1.1.4](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.3...1.1.4) (2023-04-12)


##### Bug Fixes

* package documentation in zip and update dependencies ([3ff19da](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/3ff19da02f76cdd4b57be87a9c13a6fea25f2733))

#### [1.1.3](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.2...1.1.3) (2022-09-09)


##### Bug Fixes

* update README.adoc ([cdccfdc](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/cdccfdc3adfe2f99c442e7515d139fdbfde564b2))

#### [1.1.2](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.1...1.1.2) (2022-05-27)


##### Bug Fixes

* bump dependencies & improve a little the README ([b8eedd3](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/b8eedd3a2102d8fe80c84ff37a4234cf38308023))

#### [1.1.1](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.0...1.1.1) (2022-05-11)


##### Bug Fixes

* assign policy to the 'others' category ([a149501](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/a14950109ace66d31bfda569101cf716a3370d17))

### [[secure]](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.0.1...[secure]) (2022-01-21)


##### Bug Fixes

* **schema:** bad format for exclusiveMaximum/Minimum ([9da5d40](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/9da5d40bdc7509a05a56bc3ea4532afea7a2c902))


##### Features

* **perf:** adapt policy for new classloader system ([aa9e0ee](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/aa9e0ee08ea59c255be15fc717af50e6657b7bc7)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)


# [2.1.0](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/2.0.0...2.1.0) (2026-08-31)


### Bug Fixes

* apply schema defaults to policy configuration ([d6d4731](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/d6d473189a82f37cc8f728e83d6ccad8e9e36219))
* hold the circuit in the registry rather than in a field ([73de81d](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/73de81de711e0707e2569de78239915cb81ee8f0))
* measure call duration from the invocation start ([faed663](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/faed663dfa97bc56f17b24a21df78e62a9ca991d))
* measure v3 engine call duration with its own clock ([d865251](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/d865251aefac70a9f72dcfef358a86952fc97417))


### Features

* expose maxWaitDurationInHalfOpenState ([ccc62ef](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/ccc62ef86f58ec35594b95c0ffc9bf1939fcb02d))
* expose minimumNumberOfCalls ([07644be](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/07644beeb0b809cf95dcd3c2231e0405921afcdf))
* expose permittedNumberOfCallsInHalfOpenState ([f2768ef](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/f2768efb5fe83e60c3d8934688fff186e4d253c6))
* record interrupted calls as failures when enabled ([eadccf9](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/eadccf9f49d55da7918f07b1a5aec89a981c842a))

# [2.0.0](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.5...2.0.0) (2025-02-13)


### chore

* **deps:** bump gravitee-parent to 22.2.4 ([518d7df](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/518d7dfa4d7d052781dd77f93350706839230ce7))


### Features

* support reactive engine ([d7306bb](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/d7306bb8e45c56e73346f010ae76a8d4e3c1a9b5))


### BREAKING CHANGES

* **deps:** require JDK 17

## [1.1.5](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.4...1.1.5) (2023-07-20)


### Bug Fixes

* update policy description ([8d6273f](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/8d6273f180d14412a295986f1193420e9e441a01))

## [1.1.4](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.3...1.1.4) (2023-04-12)


### Bug Fixes

* package documentation in zip and update dependencies ([3ff19da](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/3ff19da02f76cdd4b57be87a9c13a6fea25f2733))

## [1.1.3](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.2...1.1.3) (2022-09-09)


### Bug Fixes

* update README.adoc ([cdccfdc](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/cdccfdc3adfe2f99c442e7515d139fdbfde564b2))

## [1.1.2](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.1...1.1.2) (2022-05-27)


### Bug Fixes

* bump dependencies & improve a little the README ([b8eedd3](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/b8eedd3a2102d8fe80c84ff37a4234cf38308023))

## [1.1.1](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.1.0...1.1.1) (2022-05-11)


### Bug Fixes

* assign policy to the 'others' category ([a149501](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/a14950109ace66d31bfda569101cf716a3370d17))

# [[secure]](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/compare/1.0.1...[secure]) (2022-01-21)


### Bug Fixes

* **schema:** bad format for exclusiveMaximum/Minimum ([9da5d40](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/9da5d40bdc7509a05a56bc3ea4532afea7a2c902))


### Features

* **perf:** adapt policy for new classloader system ([aa9e0ee](https://github.com/gravitee-io/gravitee-policy-circuit-breaker/commit/aa9e0ee08ea59c255be15fc717af50e6657b7bc7)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)

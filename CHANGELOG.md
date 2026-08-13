# Changelog

All notable changes to this project will be documented in this file. See [commit-and-tag-version](https://github.com/absolute-version/commit-and-tag-version) for commit guidelines.

## 0.7.0 (2026-08-13)


### Dependencies

* **deps:** Update api to release-0.6.0
* **deps:** Update container-tools to release-0.8.0
* **deps:** Update DevKit to release-3.11.0


### Bug Fixes

* Verify raw entry bytes in TLedger response match the request

## 0.6.0 (2026-07-23)


### Features

* Update root certificate validity to expire on 2027-03-02

## 0.5.0 (2026-07-20)


### Dependencies

* **deps:** Update DevKit to release-3.10.0
* **deps:** Update DevKit to release-3.9.0


### Features

* Add 10 sec timeout for tLedger calls
* Add AutoBuilder support for AwsInstanceMetadata in PES
* Add metric that shows number of endorsements
* Add metrics-exporter configuration
* Add support for audience without path
* Add timeout for OidcDiscoveryFetcher.fetchJwksUri
* Align authentication status metrics to be that same as in TCA
* Change MBS metrics from counter to gauge
* Enforce OIDC audience check with global and regional hostnames
* Expose main certificate validity metric
* Extract trust domain from root cert for OIDC validation
* Integrate MBS metrics
* Introduce metrics module and endpoint
* Introduce Metrics module to support custom metrics
* Remove legacy claim type support
* Update container-tools and fix Prometheus setup
* Update container-tools to fix enclave watcher resets
* Update MBS. Set SKI in root cert. Set cache-control on backup
* Update root cert validity to be 180 days
* Update root certificate validity to expire on 2027-05-02
* Validate endorsement expiration against root certificate


### Bug Fixes

* Align BouncyCastle dependencies with modern jdk18on
* Fetch the PES root cert as pem format in integration tests
* return 403 Forbidden on OIDC audience validation failure

## 0.4.0 (2026-06-02)


### Features

* Add publisher and workload ID validation
* Add support for new claims .md location


### Documentation

* Add .md files describing claims supported by PES

## 0.3.0 (2026-05-29)


### Dependencies

* **deps:** Update DevKit to release-3.8.0


### Features

* Update operator/ part of PES' root cert spiffe_id
* update root cert SPIFFE ID format


### Bug Fixes

* Bump submodule mbs to store root_cert as PEM format

## 0.2.0 (2026-05-27)


### Dependencies

* **deps:** Update DevKit to release-3.6.0
* **deps:** Update DevKit to release-3.7.0


### Features

* enable enclave logging and clean up enclave build boilerplate

## 0.1.0 (2026-05-13)


### Features

* Initial release

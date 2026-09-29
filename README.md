# mod-tlr

Copyright (C) 2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Goal

FOLIO compatible title level requests functionality.

### Environment variables

| Name                          | Default value             | Description                                                                                                                                                                           |
|:------------------------------|:--------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JAVA_OPTIONS                  | -XX:MaxRAMPercentage=66.0 | Java options                                                                                                                                                                          |
| DB_HOST                       | postgres                  | Postgres hostname                                                                                                                                                                     |
| DB_PORT                       | 5432                      | Postgres port                                                                                                                                                                         |
| DB_USERNAME                   | postgres                  | Postgres username                                                                                                                                                                     |
| DB_PASSWORD                   | postgres                  | Postgres username password                                                                                                                                                            |
| DB_DATABASE                   | okapi_modules             | Postgres database name                                                                                                                                                                |
| KAFKA_HOST                    | kafka                     | Kafka broker hostname                                                                                                                                                                 |
| KAFKA_PORT                    | 9092                      | Kafka broker port                                                                                                                                                                     |
| KAFKA_SECURITY_PROTOCOL       | PLAINTEXT                 | Kafka security protocol used to communicate with brokers (SSL or PLAINTEXT)                                                                                                           |
| KAFKA_SSL_KEYSTORE_LOCATION   | -                         | The location of the Kafka key store file. This is optional for client and can be used for two-way authentication for client.                                                          |
| KAFKA_SSL_KEYSTORE_PASSWORD   | -                         | The store password for the Kafka key store file. This is optional for client and only needed if 'ssl.keystore.location' is configured.                                                |
| KAFKA_SSL_TRUSTSTORE_LOCATION | -                         | The location of the Kafka trust store file.                                                                                                                                           |
| KAFKA_SSL_TRUSTSTORE_PASSWORD | -                         | The password for the Kafka trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled.                            |
| MODULE_VERSION                | Maven project version     | Exact module version used in version-specific Kafka consumer groups and entitlement lookups. In a mixed-version deployment this is required and must exactly match the `MODULE_VERSION` supplied to the associated sidecar, including the CI build number. |
| KAFKA_TENANT_FILTER_ENABLED   | false                     | Enables consortium-aware Kafka tenant-entitlement filtering. Keep disabled until the matching released `folio-spring-kafka` version is adopted and mixed-version tests pass.           |
| KAFKA_TENANT_FILTER_TENANT_DISABLED_STRATEGY | skip       | Strategy when an event's resolved central tenant is not entitled to this module version: `accept`, `skip`, or `fail`.                                                                  |
| KAFKA_TENANT_FILTER_ALL_TENANTS_DISABLED_STRATEGY | fail   | Strategy when no tenants are entitled to this module version: `accept`, `skip`, or `fail`.                                                                                            |
| KAFKA_TENANT_FILTER_ENTITLEMENT_REFRESH_INTERVAL_SECONDS | 900 | Interval in seconds for reconciling the entitled-tenant cache with the sidecar.                                                                                                    |
| SYSTEM_USER_USERNAME          | mod-tlr                   | Username for `mod-tlr` system user                                                                                                                                                    |
| SYSTEM_USER_PASSWORD          | -                         | Password for `mod-tlr` system user (not required for dev envs)                                                                                                                        |
| SYSTEM_USER_ENABLED           | true                      | Defines if system user must be created at service tenant initialization                                                                                                               |
| OKAPI_URL                     | -                         | OKAPI URL used to login system user, required                                                                                                                                         |
| ENV                           | folio                     | The logical name of the deployment, must be unique across all environments using the same shared Kafka/Elasticsearch clusters, `a-z (any case)`, `0-9`, `-`, `_` symbols only allowed |

#### Mixed-version Kafka support

Kafka consumer group IDs include `MODULE_VERSION`, allowing multiple `mod-tlr` versions to receive the same events independently. The Maven project version is used only as a local fallback. Hosting providers must set `MODULE_VERSION` on the module container to the exact version used by its sidecar; otherwise tenant-entitlement filtering can query the wrong module ID.

`mod-tlr` supplies a custom `tenantAwareMessageFilter` because it is entitled in a consortium's central tenant while circulation, inventory, and user events carry the originating member tenant. When filtering is enabled, the filter resolves the member to its central tenant through `ConsortiumService`, then applies the shared entitlement cache and configured disabled-tenant strategies. The original Kafka record remains unchanged for normal listener processing. Filtering is disabled by default until a released, compatible `folio-spring-kafka` artifact and mixed-version deployment tests are available. See [the MODTLR-324 assessment](docs/MODTLR-324-mixed-version-assessment.md) for rollout constraints and failure semantics.

## Further information

### Issue tracker

Project [MODTLR](https://issues.folio.org/browse/MODTLR).

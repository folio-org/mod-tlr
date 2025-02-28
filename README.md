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
| SYSTEM_USER_USERNAME          | mod-tlr                   | Username for `mod-tlr` system user                                                                                                                                                    |
| SYSTEM_USER_PASSWORD          | -                         | Password for `mod-tlr` system user (not required for dev envs)                                                                                                                        |
| SYSTEM_USER_ENABLED           | true                      | Defines if system user must be created at service tenant initialization                                                                                                               |
| OKAPI_URL                     | -                         | OKAPI URL used to login system user, required                                                                                                                                         |
| ENV                           | folio                     | The logical name of the deployment, must be unique across all environments using the same shared Kafka/Elasticsearch clusters, `a-z (any case)`, `0-9`, `-`, `_` symbols only allowed |

## Further information

### Issue tracker

Project [MODTLR](https://issues.folio.org/browse/MODTLR).

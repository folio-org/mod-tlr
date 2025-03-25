## 1.0.10 2025-03-25
* Handle intermediate request updates (MODTLR-150)

## 1.0.9 2025-03-10
* Add force loan policy support during check-out (MODTLR-152)
* Check-out API (MODTLR-151)

## 1.0.8 2025-03-03
* Fix ECS TLRs for instance with holding and no items (MODTLR-156)
* Add Kafka TLS support (MODTLR-159)

## 1.0.7 2025-02-18
* Add missing tokens to search slips for TLRs (MODTLR-131)
* Create shadow instance in primary request tenant for secure requests (MODTLR-141)
* Allow primary ECS requests for items which were previously requested, loaned and returned (MODTLR-144)

## 1.0.6 2025-02-13
* Fix request update event handling (MODTLR-138)
* Add patronComments mapping to primary request (MODTLR-139)

## 1.0.5 2025-01-15
* Upgrade to Spring Boot 3.3.7, folio-spring 8.2.2 (MODTLR-125)

## 1.0.4 2025-01-09
* Fix dependencies and permissions, move interface `consortia` to optional (MODTLR-123)

## 1.0.3 2024-12-30
* DCB transaction status synchronization (MODTLR-112)
* Resolve central tenant ID dynamically (MODTLR-118)

## 1.0.2 2024-12-12
* Copy Secure Patron name when cloning users (MODTLR-116)
* Support for intermediate requests (MODTLR-98)
* Search Slips API (MODTLR-75)

## 1.0.1 2024-11-30
* Merge `ecs-tlr-feature` branch into `master` (MODTLR-69)
* Create pickup service point in lending tenant (MODTLR-17)
* Remove unneeded fields from the requester copy, update group (MODTLR-27)
* Fix creation of DCB transactions (MODTLR-30)
* Add `ecsRequestPhase` to primary and secondary requests (MODTLR-31)
* Add missing dependencies (MODTLR-33)
* Create allowed-service-points endpoint (MODTLR-25)
* Upgrade to Spring Boot 3.3.0 (MODTLR-37)
* Create borrowing transaction (MODTLR-47)
* Allowed service points - data tenant interaction (MODTLR-26)
* Add allow-service-points permission (MODTLR-49)
* Consume and handle patron group domain events (MODTLR-48)
* Update DCB transaction upon request update event (MODTLR-34)
* Create borrowing transaction in mod-dcb (MODTLR-47)
* Call Publications API when ECS TLR setting updated (MODTLR-44)
* Use `patronGroupId` parameter instead of `requesterId` (MODTLR-50)
* Propagate changes from primary to secondary request (MODTLR-41)
* Close ECS TLR when both Primary and Secondary requests are cancelled (MODTLR-40)
* Switch additionalProperties to true to defined schemas (MODTLR-52)
* Create DCB transactions immediately when ECS TLR is created (MODTLR-51)
* Reorder secondary requests - part 1 (MODTLR-42)
* Reorder secondary requests - part 2 (MODTLR-43)
* Use `requesterId` instead of `patronGroupId` in Allowed Service Points API (MODTLR-54)
* Autogenerate ECS TLR ID when client doesn't provide it (MODTLR-53)
* Support for operation `replace` in Allowed Service Points API (MODTLR-56)
* Add system user variables, bump folio-spring-system-user version (MODTLR-57)
* Get token from headers as a fallback (MODTLR-67)
* Allowed service points API for item-level requests (MODTLR-59)
* Fix remaining ECS TLR scenarios and create tests (MODTLR-73)
* Pick Slips API - part 1 (MODTLR-58)
* Pick Slips API - part 2 (MODTLR-79)
* Handle null circulation item correctly (MODTLR-84)
* Allowed service points - only use lending side circulation rules (MODTLR-68)
* Incorporate support for Locate ECS requests (MODTLR-82)
* Fix permissions for mod-patron (MODTLR-93)
* Rename mod-settings permissions (MODTLR-97)
* Consume and handle user UPDATE events (MODTLR-64)
* Revert mod-settings permissions (MODTLR-99)
* Add system user to module descriptor (MODTLR-100)
* Remove okapi token parsing (MODTLR-104)
* Allowed service points - accept `patronGroupId` parameter (MODTLR-102)

## 1.0.0 2024-10-30
* Update folio-spring-support version (MODTLR-71)
* Upgrade packages in Dockerfile (MODTLR-36)
* Add missing permissions to Tenant API definition (MODTLR-28)
* Create a new endpoint `tlr/settings` (GET, PUT methods) to manage ECS TLR settings (MODTLR-24)
* Create primary request in borrowing tenant (MODTLR-22)
* Create DCB LENDER transaction when secondary request is linked to an item (MODTLR-20)
* Create a client to update DCB transaction statuses (MODTLR-19)
* Implement retry creation on failure (MODTLR-18)
* Handle request update event to update ECS TLR (MODTLR-13)
* Create a client for DCB transaction creation (MODTLR-12)
* Dynamically pick tenant for secondary TLR (MODTLR-11)
* Support for cross-tenant requests (MODTLR-10)
* Create PUT, DELETE endpoints for updating ECS TLRs (MODTLR-9)
* Add Kafka support (MODTLR-8)
* Add the client for mod-search (MODTLR-7)
* Create TLR processing timer (MODTLR-6)
* Add `/tlr/ecs-tlr` POST endpoint (MODTLR-5)
* Add PD disclosure form (MODTLR-3)
* Use GitHub workflows `api-lint` and `api-schema-lint` and `api-doc` (MODTLR-2)
* Initial structure and implementation (MODTLR-1)

## Module 'mod-tlr' Self-Evaluation Checklist

Based on [FOLIO Module Acceptance Values and Criteria version 3.0](https://github.com/folio-org/tech-council/blob/master/MODULE_ACCEPTANCE_CRITERIA.MD)

## Administrative
* [ ] Listed by the Product Council on [Functionality Evaluated by the PC](https://wiki.folio.org/display/PC/Functionality+Evaluated+by+the+PC) with a positive evaluation result.

## Shared/Common
* [ ] Uses Apache 2.0 license (2)
* [ ] Module build MUST produce a valid module descriptor (3, 5)
  * _This is not applicable to libraries_
* [ ] Inclusion of third party dependencies complies with [ASF 3rd Party License Policy](https://apache.org/legal/resolved.html) (2)
  * Uses README for [Category B Appropriately Labelled Condition](https://apache.org/legal/resolved.html#appropriately-labelled-condition)
  * org.z3950.zing:cql-java is allowed if appropriately labelled, even if it is LGPL-2.1-only
  * org.marc4j:marc4j is allowed if appropriately labelled, even if it is LGPL-2.1-or-later
  * org.hibernate.* is allowed if appropriately labelled, even if it is LGPL-2.1-or-later
  * _note: If a library declares multiple licenses in its pom.xml, [only one of them needs to comply](https://maven.apache.org/ref/3.9.11/maven-model/maven.html#project)._
* [ ] Installation documentation is included (11)
  * -_note: read more at https://github.com/folio-org/mod-search/blob/master/README.md_
  * _This is not applicable to libraries_
* [ ] [Personal data form](https://github.com/folio-org/personal-data-disclosure) is completed, accurate, and provided as PERSONAL_DATA_DISCLOSURE.md file (6)
  * _This is not applicable to libraries_
* [ ] Sensitive and environment-specific information is not checked into git repository (6)
* [ ] Written in a language and framework from the [officially supported technologies](https://wiki.folio.org/display/TC/Officially+Supported+Technologies) page[^1] (3, 5)
* [ ] Uses FOLIO interfaces already provided by previously accepted modules _e.g. a UI module cannot be accepted that relies on an interface only provided by a back end module that hasn’t been accepted yet_ (3, 5, 12)
  * _This is not applicable to libraries_
* [ ] Must not depend on a FOLIO library that has not been approved through the TCR process
* [ ] Gracefully handles the absence of third party systems or related configuration. (3, 5, 12)
* [ ] Sonarqube hasn't identified any security issues, any high or greater severity issues, or excessive (>3%) duplication (6); and any disabled or intentionally ignored rules/recommendations are reasonably justified.
  * See [Rule Customization](https://dev.folio.org/guides/code-analysis/#rule-customization) details.
* [ ] Uses [officially supported](https://wiki.folio.org/display/TC/Officially+Supported+Technologies) build tools (3, 5, 13)
* [ ] Unit tests have 80% coverage or greater, and are based on [officially supported technologies](https://wiki.folio.org/display/TC/Officially+Supported+Technologies)[^1] (3, 4)
* [ ] Assigned to exactly one application descriptor within the FOLIO Community LSP Platform, specified in the Jira task for this module evaluation (3, 5)
  * _The FOLIO Community LSP Platform is defined at https://github.com/folio-org/platform-lsp._
  * _This can be evaluated by searching application descriptors across the folio-org GitHub organization (considering those applications part of the community platform) and confirming that this module only appears in the application descriptor of the one specified application._

## Backend

* [ ] Module’s repository includes a compliant Module Descriptor (3, 5)
  * -_note: read more at https://github.com/folio-org/okapi/blob/master/okapi-core/src/main/raml/ModuleDescriptor.json_
* [ ] For each consumed API the module descriptor MUST include the interface requirement in the `"requires"` or `"optional"` section (3, 5)
  * _This is not applicable to libraries_
* [ ] Module includes executable implementations of all endpoints in the provides section of the Module Descriptor
* [ ] Environment vars are documented in the ModuleDescriptor (5, 11)
  * -_note: read more at https://dev.folio.org/guides/module-descriptor/#docker-env_
* [ ] If a module provides interfaces intended to be consumed by other FOLIO Modules, they must be defined in the Module Descriptor "provides" section, and must conform to FOLIO [interface naming conventions](https://dev.folio.org/guidelines/naming-conventions/#interfaces) (3, 5)
* [ ] All API endpoints are documented in OpenAPI (11)
* [ ] All API endpoints protected with appropriate permissions as per the following guidelines and recommendations, e.g. avoid using *.all permissions, all necessary module permissions are assigned, etc. (6)
  * -_note: read more at https://dev.folio.org/guidelines/naming-conventions/ and https://wiki.folio.org/display/DD/Permission+Set+Guidelines_
* [ ] Module provides reference data (if applicable), e.g. if there is a controlled vocabulary where the module requires at least one value (3, 16)
* [ ] If provided, integration (API) tests must be written in an [officially supported technology](https://wiki.folio.org/display/TC/Officially+Supported+Technologies)[^1] (3, 4)
  * -_note: while it's strongly recommended that modules implement integration tests, it's not a requirement_
  * -_note: these tests are defined in https://github.com/folio-org/folio-integration-tests_
* [ ] Data is segregated by tenant at the storage layer (6, 7)
* [ ] The module doesn’t access data in DB schemas other than its own and public. Exception: [FOLIO Query Machine](https://folio-org.atlassian.net/wiki/spaces/TC/pages/852852742/0011-Folio+Query+Machine+FQM) and its modules are the mechanism through which we provide read access across DB storage boundaries. (6, 7)
* [ ] Any dependencies, other than on defined interfaces, are declared in the README.md.
* [ ] The module responds with a tenant’s content based on x-okapi-tenant header (7)
* [ ] Standard GET /admin/health endpoint returning a 200 response (5)
  * -_note: read more at https://wiki.folio.org/display/DD/Back+End+Module+Health+Check+Protocol_
* [ ] High Availability (HA) compliant (5, 14, 15)
  * Possible red flags:
    * Connection affinity / sticky sessions / etc. are used
    * Local container storage is used
    * Services are stateful
* [ ] Module only uses infrastructure / platform technologies on the [officially supported technologies](https://wiki.folio.org/display/TC/Officially+Supported+Technologies) list.[^1]
  * _e.g. PostgreSQL, ElasticSearch, etc._ (3, 5, 12)

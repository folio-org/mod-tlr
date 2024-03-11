package org.folio.api;

import org.junit.jupiter.api.Test;

class OpenRequestsProcessingApiTest extends BaseIT {
  private static final String PROCESS_OPEN_REQUESTS_URL = "/tlr/ecs-tlr-processing";

  @Test
  void getByIdNotFound() {
    doPost(PROCESS_OPEN_REQUESTS_URL, "").expectStatus().is2xxSuccessful();
  }
}

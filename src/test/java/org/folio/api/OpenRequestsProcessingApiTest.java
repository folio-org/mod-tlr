package org.folio.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OpenRequestsProcessingApiTest extends BaseIT {
  private static final String PROCESS_OPEN_REQUESTS_URL = "/tlr/ecs-tlr-processing";

  @Test
  void getByIdNotFound() throws Exception {
    mockMvc.perform(
        post(PROCESS_OPEN_REQUESTS_URL)
          .headers(defaultHeaders())
          .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().is2xxSuccessful());
  }
}

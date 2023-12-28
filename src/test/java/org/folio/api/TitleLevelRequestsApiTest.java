package org.folio.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TitleLevelRequestsApiTest extends BaseIT {
  private static final String TLR_URL = "/title-level-requests/";

  @Test
  void getByIdNotFound() throws Exception {
    mockMvc.perform(
        get(TLR_URL + UUID.randomUUID())
          .headers(defaultHeaders())
          .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isNotFound());
  }
}

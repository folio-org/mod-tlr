package org.folio.model;

import org.folio.client.feign.SearchClient.Instance;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultListTest {

  @Test
  void canCreateResultList() {
    assertTrue(ResultList.asSinglePage(
        new Instance(UUID.randomUUID().toString(), "tenant1"),
        new Instance(UUID.randomUUID().toString(), "tenant2"))
      .getTotalRecords() > 0);
  }

  @Test
  void canCreateEmptyResultList() {
    assertEquals(0, ResultList.empty().getTotalRecords());
  }
}

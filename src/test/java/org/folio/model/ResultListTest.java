package org.folio.model;

import org.folio.client.feign.SearchClient.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ResultListTest {

  @Test
  void canCreateResultList() {
    ResultList<Instance> mockResult = ResultList.asSinglePage(List.of(new Instance(UUID.randomUUID().toString(), "tenant1"), new Instance(UUID.randomUUID().toString(), "tenant2")));
    assertTrue(mockResult.getTotalRecords() > 0);
  }

  @Test
  void canCreateEmptyResultList() {
    ResultList<Instance> mockResult = ResultList.empty();
    assertEquals(0, mockResult.getTotalRecords());
  }
}
package org.folio.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.support.CqlQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchClientTest {
  @Mock
  private SearchClient searchClient;

  @Test
  void canGetInstances() {
    SearchInstance instance = new SearchInstance().id(UUID.randomUUID().toString()).tenantId("tenant1");
    SearchInstancesResponse mockResponse = new SearchInstancesResponse()
      .instances(List.of(instance))
      .totalRecords(1);
    when(searchClient.searchInstances(any(CqlQuery.class), anyBoolean())).thenReturn(mockResponse);
    var response = searchClient.searchInstances(
      CqlQuery.exactMatch("id", UUID.randomUUID().toString()), true);
    assertNotNull(response);
    assertTrue(response.getTotalRecords() > 0);
  }
}

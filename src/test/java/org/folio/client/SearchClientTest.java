package org.folio.client;

import org.folio.support.CqlQuery;
import org.folio.client.feign.SearchClient;
import org.folio.model.ResultList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SearchClientTest {
  @Mock
  private SearchClient searchClient;

  @Test
  void testGetInstances() {
    SearchClient.Instance instance = new SearchClient.Instance(UUID.randomUUID().toString(), "tenant1");
    ResultList<SearchClient.Instance> mockResult = ResultList.asSinglePage(List.of(instance));
    when(searchClient.searchInstances(any(CqlQuery.class), anyBoolean())).thenReturn(mockResult);
    var response = searchClient.searchInstances(CqlQuery.exactMatch("id", UUID.randomUUID().toString()), true);
    assertNotNull(response);
    assertTrue(response.getTotalRecords() > 0);
  }
}

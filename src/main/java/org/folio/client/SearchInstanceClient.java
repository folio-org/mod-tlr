package org.folio.client;

import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "search/instances")
public interface SearchInstanceClient extends GetByQueryClient<SearchInstancesResponse> {

  // max limit allowed by Search Instances API
  int DEFAULT_SEARCH_INSTANCES_LIMIT = 500;

  @Override
  @GetExchange
  SearchInstancesResponse getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange
  SearchInstancesResponse searchInstances(@RequestParam("query") CqlQuery cql,
    @RequestParam("expandAll") boolean expandAll, @RequestParam("limit") int limit);

  default SearchInstancesResponse searchInstance(String instanceId) {
    return searchInstances(CqlQuery.exactMatch("id", instanceId), true, 1);
  }

  default SearchInstancesResponse searchInstances(CqlQuery query) {
    return getByQuery(query, DEFAULT_SEARCH_INSTANCES_LIMIT);
  }

}

package org.folio.service.impl;

import lombok.RequiredArgsConstructor;
import org.folio.client.feign.SearchClient;
import org.folio.service.SearchService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.folio.support.CqlQuery.exactMatch;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

  private final SearchClient searchClient;
  @Override
  public Optional<List<String>> getTenantsByInstanceId(UUID instanceId) {
    var resultList = searchClient.searchInstances(exactMatch("id", instanceId.toString()));
    if(resultList.getTotalRecords() > 0) {
      return Optional.of(resultList.getResult().stream().map(SearchClient.Instance::getId).toList());
    }
    return Optional.empty();
  }
}

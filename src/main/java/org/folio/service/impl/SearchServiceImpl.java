package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.SearchInstanceClient;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.service.SearchService;
import org.folio.support.BulkFetcher;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class SearchServiceImpl implements SearchService {

  private final SearchInstanceClient searchInstanceClient;

  @Override
  public Collection<SearchInstance> searchInstances(CqlQuery commonQuery, String idIndex,
    Collection<String> ids) {

    log.info("searchInstances:: searching instances by query and index: query={}, index={}, ids={}",
      commonQuery, idIndex, ids);
    log.debug("searchInstances:: ids={}", ids);
    Collection<SearchInstance> instances = BulkFetcher.fetch(searchInstanceClient, commonQuery,
      idIndex, ids, SearchInstancesResponse::getInstances);
    log.info("searchInstances:: found {} instances", instances::size);
    return instances;
  }
}

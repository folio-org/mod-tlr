package org.folio.service.impl;

import static org.folio.domain.dto.BatchIds.IdentifierTypeEnum.BARCODE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.folio.client.feign.SearchInstanceClient;
import org.folio.client.feign.ConsortiumSearchClient;
import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.domain.dto.ConsortiumItems;
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
  private final ConsortiumSearchClient consortiumSearchClient;

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

  @Override
  public ConsortiumItems searchItems(BatchIds batchIds) {
    log.info("searchItem:: searching items: {}", batchIds);
    return consortiumSearchClient.searchItems(batchIds);
  }

  @Override
  public Optional<ConsortiumItem> searchItem(String itemBarcode) {
    log.info("searchItem:: searching item by barcode: {}", itemBarcode);

    Optional<ConsortiumItem> consortiumItem = searchItems(new BatchIds(BARCODE, List.of(itemBarcode)))
      .getItems()
      .stream()
      .findFirst();

    consortiumItem.ifPresentOrElse(
      item -> log.info("searchItem:: item found: id={}, tenantId={}", item.getId(), item.getTenantId()),
      () -> log.info("searchItem:: item with barcode {} was not found", itemBarcode));

    return consortiumItem;
  }
}

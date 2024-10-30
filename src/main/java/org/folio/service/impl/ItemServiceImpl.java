package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.ItemClient;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Items;
import org.folio.service.BulkFetchingService;
import org.folio.service.ItemService;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ItemServiceImpl implements ItemService {
  private final ItemClient itemClient;
  private final BulkFetchingService bulkFetchingService;

  @Override
  public Collection<Item> findItems(CqlQuery query, String idIndex, Collection<String> ids) {
    log.info("findItems:: searching items by {} IDs: query={}, idIndex={}", ids.size(), query, idIndex);
    Collection<Item> items = bulkFetchingService.fetch(itemClient, query, idIndex, ids, Items::getItems);
    log.info("findItems:: found {} items", items::size);
    return items;
  }
}

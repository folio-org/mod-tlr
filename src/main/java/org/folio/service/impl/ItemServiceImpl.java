package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.ItemClient;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Items;
import org.folio.service.ItemService;
import org.folio.support.BulkFetcher;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ItemServiceImpl implements ItemService {
  private final ItemClient itemClient;

  @Override
  public Collection<Item> findItems(CqlQuery query, String idIndex, Collection<String> ids) {
    log.info("findItems:: searching items by query and index: query={}, index={}, ids={}",
      query, idIndex, ids.size());
    log.debug("findItems:: ids={}", ids);
    Collection<Item> items = BulkFetcher.fetch(itemClient, query, idIndex, ids, Items::getItems);
    log.info("findItems:: found {} items", items::size);
    return items;
  }
}

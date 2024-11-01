package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.Item;
import org.folio.support.CqlQuery;

public interface ItemService {
  Collection<Item> findItems(CqlQuery query, String idIndex, Collection<String> ids);
}

package org.folio.service;

import java.util.Collection;
import java.util.Optional;

import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.domain.dto.ConsortiumItems;
import org.folio.domain.dto.SearchInstance;
import org.folio.support.CqlQuery;

public interface SearchService {
  Collection<SearchInstance> searchInstances(CqlQuery commonQuery, String idIndex,
    Collection<String> ids);
  ConsortiumItems searchItems(BatchIds batchIds);
  Optional<ConsortiumItem> searchItem(String itemBarcode);
}

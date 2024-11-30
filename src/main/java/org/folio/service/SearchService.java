package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.SearchInstance;
import org.folio.support.CqlQuery;

public interface SearchService {
  Collection<SearchInstance> searchInstances(CqlQuery commonQuery, String idIndex,
    Collection<String> ids);
}

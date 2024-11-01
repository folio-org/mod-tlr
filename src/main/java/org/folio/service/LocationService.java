package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.Location;
import org.folio.support.CqlQuery;

public interface LocationService {
  Collection<Location> findLocations(CqlQuery query);
}

package org.folio.service.impl;

import java.util.Collection;
import java.util.List;

import org.folio.client.feign.LocationClient;
import org.folio.domain.dto.Location;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class LocationServiceImpl implements org.folio.service.LocationService {

  private final LocationClient locationClient;

  @Override
  public Collection<Location> findLocations(CqlQuery query) {
    log.info("findLocations:: searching locations by query: {}", query);
    List<Location> locations = locationClient.getByQuery(query).getLocations();
    log.info("findLocations:: found {} locations", locations::size);
    return locations;
  }
}

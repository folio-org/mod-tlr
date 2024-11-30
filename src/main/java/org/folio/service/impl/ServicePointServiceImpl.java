package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.ServicePointClient;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.ServicePoints;
import org.folio.service.ServicePointService;
import org.folio.support.BulkFetcher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ServicePointServiceImpl implements ServicePointService {

  private final ServicePointClient servicePointClient;

  @Override
  public ServicePoint find(String servicePointId) {
    log.info("find:: looking up service point {}", servicePointId);
    return servicePointClient.getServicePoint(servicePointId);
  }

  @Override
  public Collection<ServicePoint> find(Collection<String> servicePointIds) {
    log.info("find:: searching service points by {} IDs", servicePointIds::size);
    log.debug("find:: ids={}", servicePointIds);
    Collection<ServicePoint> servicePoints = BulkFetcher.fetch(servicePointClient, servicePointIds,
      ServicePoints::getServicepoints);
    log.info("find:: found {} service points", servicePoints::size);
    return servicePoints;
  }

  @Override
  public ServicePoint create(ServicePoint servicePoint) {
    log.info("create:: creating service point {}", servicePoint.getId());
    return servicePointClient.postServicePoint(servicePoint);
  }
}

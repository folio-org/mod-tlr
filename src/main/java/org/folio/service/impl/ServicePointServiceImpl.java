package org.folio.service.impl;

import org.folio.client.feign.ServicePointClient;
import org.folio.domain.dto.ServicePoint;
import org.folio.service.ServicePointService;
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
  public ServicePoint create(ServicePoint servicePoint) {
    log.info("create:: creating service point {}", servicePoint.getId());
    return servicePointClient.postServicePoint(servicePoint);
  }
}

package org.folio.service.impl;

import org.folio.client.feign.ServicePointsClient;
import org.folio.domain.dto.ServicePoint;
import org.folio.service.ServicePointService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ServicePointServiceImpl implements ServicePointService {

  private final ServicePointsClient servicePointsClient;

  @Override
  public ServicePoint find(String servicePointId) {
    log.info("find:: looking up service point {}", servicePointId);
    return servicePointsClient.getServicePoint(servicePointId);
  }

  @Override
  public ServicePoint create(ServicePoint servicePoint) {
    log.info("create:: creating service point {}", servicePoint.getId());
    return servicePointsClient.postServicePoint(servicePoint);
  }
}

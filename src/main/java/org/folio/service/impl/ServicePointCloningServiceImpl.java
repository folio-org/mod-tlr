package org.folio.service.impl;

import org.folio.domain.dto.ServicePoint;
import org.folio.service.ServicePointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ServicePointCloningServiceImpl extends CloningServiceImpl<ServicePoint> {

  private static final String SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX = "DCB_";

  private final ServicePointService servicePointService;

  public ServicePointCloningServiceImpl(@Autowired ServicePointService servicePointService) {

    super(ServicePoint::getId);
    this.servicePointService = servicePointService;
  }

  @Override
  protected ServicePoint find(String userId) {
    return servicePointService.find(userId);
  }

  @Override
  protected ServicePoint create(ServicePoint clone) {
    return servicePointService.create(clone);
  }

  @Override
  protected ServicePoint buildClone(ServicePoint original) {
    ServicePoint clone = new ServicePoint()
      .id(original.getId())
      .name(SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX + original.getName())
      .code(original.getCode())
      .discoveryDisplayName(original.getDiscoveryDisplayName())
      .pickupLocation(original.getPickupLocation())
      .holdShelfExpiryPeriod(original.getHoldShelfExpiryPeriod());

    log.debug("buildClone:: result: {}", () -> clone);
    return clone;
  }
}

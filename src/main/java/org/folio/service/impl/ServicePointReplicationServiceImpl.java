package org.folio.service.impl;

import org.folio.domain.dto.ServicePoint;
import org.folio.service.ServicePointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ServicePointReplicationServiceImpl extends ReplicationServiceImpl<ServicePoint> {

  private static final String SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX = "DCB_";

  @Autowired
  private ServicePointService servicePointService;

  public ServicePointReplicationServiceImpl() {
    super(ServicePoint::getId);
  }

  @Override
  protected ServicePoint find(String userId) {
    return servicePointService.find(userId);
  }

  @Override
  protected ServicePoint create(ServicePoint replica) {
    return servicePointService.create(replica);
  }

  @Override
  protected ServicePoint buildReplica(ServicePoint original) {
    ServicePoint servicePoint = new ServicePoint()
      .id(original.getId())
      .name(SECONDARY_REQUEST_PICKUP_SERVICE_POINT_NAME_PREFIX + original.getName())
      .code(original.getCode())
      .discoveryDisplayName(original.getDiscoveryDisplayName());

    log.debug("buildReplica:: result: {}", () -> servicePoint);
    return servicePoint;
  }
}

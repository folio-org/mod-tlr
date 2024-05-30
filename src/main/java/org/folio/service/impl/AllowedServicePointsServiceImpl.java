package org.folio.service.impl;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.RequestOperation;
import org.folio.service.AllowedServicePointsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class AllowedServicePointsServiceImpl implements AllowedServicePointsService {

  private final CirculationClient circulationClient;

  @Override
  public AllowedServicePointsResponse getAllowedServicePoints(RequestOperation operation,
    String requesterId, String instanceId) {

    log.debug("getAllowedServicePoints:: params: requesterId={}, instanceId={}", requesterId,
      instanceId);

    return circulationClient.allowedServicePoints(requesterId, instanceId,
      operation.toString().toLowerCase(), true);
  }

}

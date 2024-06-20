package org.folio.service;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.RequestOperation;

public interface AllowedServicePointsService {
  AllowedServicePointsResponse getAllowedServicePoints(RequestOperation operation,
    String requesterId, String instanceId);
}

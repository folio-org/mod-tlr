package org.folio.service;

import org.folio.domain.dto.AllowedServicePointsResponse;

public interface AllowedServicePointsService {
  AllowedServicePointsResponse getAllowedServicePoints(String requesterId, String instanceId);
}

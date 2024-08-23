package org.folio.service;

import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;

public interface AllowedServicePointsService {

  AllowedServicePointsResponse getAllowedServicePoints(AllowedServicePointsRequest request);
}

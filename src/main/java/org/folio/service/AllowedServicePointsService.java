package org.folio.service;

import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.RequestOperation;

public interface AllowedServicePointsService {

  AllowedServicePointsResponse getAllowedServicePoints(AllowedServicePointsRequest request);
}

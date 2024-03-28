package org.folio.service;

import org.folio.domain.dto.ServicePoint;

public interface ServicePointService {
  ServicePoint find(String id);
  ServicePoint create(ServicePoint servicePoint);
}

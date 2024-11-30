package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.ServicePoint;

public interface ServicePointService {
  ServicePoint find(String id);
  Collection<ServicePoint> find(Collection<String> servicePointIds);
  ServicePoint create(ServicePoint servicePoint);
}

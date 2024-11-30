package org.folio.service;

import org.folio.domain.dto.PublicationResponse;

public interface PublishCoordinatorService <T> {
  PublicationResponse updateForAllTenants(T t);
}

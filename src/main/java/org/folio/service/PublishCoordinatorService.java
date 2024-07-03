package org.folio.service;

import java.util.Optional;

public interface PublishCoordinatorService <T> {
  Optional<T> updateForAllTenants(T t);
}

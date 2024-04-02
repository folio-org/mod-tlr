package org.folio.service;

public interface ReplicationService<T> {
  T replicate(T original, String targetTenant);
}

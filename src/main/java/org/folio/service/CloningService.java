package org.folio.service;

public interface CloningService<T> {
  T clone(T original);
}

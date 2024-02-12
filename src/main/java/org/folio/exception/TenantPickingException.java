package org.folio.exception;

public class TenantPickingException extends RuntimeException {
  public TenantPickingException(String message) {
    super(message);
  }
}

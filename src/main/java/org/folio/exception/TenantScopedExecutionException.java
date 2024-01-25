package org.folio.exception;

public class TenantScopedExecutionException extends RuntimeException {
  private final String tenantId;

  public TenantScopedExecutionException(Exception cause, String tenantId) {
    super(cause);
    this.tenantId = tenantId;
  }
}

package org.folio.exception;

import lombok.Getter;

@Getter
public class TenantScopedExecutionException extends RuntimeException {
  private final String tenantId;

  public TenantScopedExecutionException(Exception cause, String tenantId) {
    super(cause);
    this.tenantId = tenantId;
  }
}

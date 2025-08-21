package org.folio.exception;

import java.util.Map;

import org.folio.domain.type.ErrorCode;

import lombok.Getter;

@Getter
public abstract class ApiException extends RuntimeException {
  private final ErrorCode code;
  private final transient Map<String, String> parameters;

  protected ApiException(String message, ErrorCode code, Map<String, String> parameters) {
    super(message);
    this.code = code;
    this.parameters = parameters;
  }

  public String getType() {
    return getClass().getSimpleName();
  }
}

package org.folio.exception;

import java.util.List;

import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;

import lombok.Getter;

@Getter
public abstract class ApiException extends RuntimeException {
  private final ErrorCode code;
  private final transient List<Parameter> parameters;

  public ApiException(String message, ErrorCode code, List<Parameter> parameters) {
    super(message);
    this.code = code;
    this.parameters = parameters;
  }

  public String getType() {
    return getClass().getSimpleName();
  }
}

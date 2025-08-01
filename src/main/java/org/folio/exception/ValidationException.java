package org.folio.exception;

import java.util.List;

import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
  private final ErrorCode code;
  private final List<Parameter> parameters;

  public ValidationException(String message, ErrorCode code, List<Parameter> parameters) {

    super(message);
    this.code = code;
    this.parameters = parameters;
  }
}

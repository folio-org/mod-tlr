package org.folio.exception;

import java.util.List;

import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;

public class BadRequestException extends ApiException {

  public BadRequestException(String message, ErrorCode code, List<Parameter> parameters) {
    super(message, code, parameters);
  }
}

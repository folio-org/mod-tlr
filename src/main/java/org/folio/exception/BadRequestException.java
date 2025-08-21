package org.folio.exception;

import java.util.Map;

import org.folio.domain.type.ErrorCode;

public class BadRequestException extends ApiException {

  public BadRequestException(String message, ErrorCode code, Map<String, String> parameters) {
    super(message, code, parameters);
  }
}

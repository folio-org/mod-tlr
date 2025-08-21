package org.folio.exception;

import java.util.Map;

import org.folio.domain.type.ErrorCode;

public class NotFoundException extends ApiException {

  public NotFoundException(String message, ErrorCode code, Map<String, String> parameters) {
    super(message, code, parameters);
  }
}

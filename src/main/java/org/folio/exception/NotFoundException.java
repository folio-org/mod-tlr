package org.folio.exception;

import java.util.List;

import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;

public class NotFoundException extends ApiException {

  public NotFoundException(String message, ErrorCode code, List<Parameter> parameters) {
    super(message, code, parameters);
  }
}

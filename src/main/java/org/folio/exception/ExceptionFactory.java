package org.folio.exception;

import java.util.List;

import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;

public class ExceptionFactory {

  public static BadRequestException badRequest(String message, ErrorCode code,
    List<Parameter> parameters) {

    return new BadRequestException(message, code, parameters);
  }

  public static NotFoundException notFound(String message, ErrorCode code,
    List<Parameter> parameters) {

    return new NotFoundException(message, code, parameters);
  }

  public static ValidationException validationError(String message, ErrorCode code,
    List<Parameter> parameters) {

    return new ValidationException(message, code, parameters);
  }

}

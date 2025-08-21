package org.folio.exception;

import java.util.Map;

import org.folio.domain.type.ErrorCode;

public class ExceptionFactory {

  public static BadRequestException badRequest(String message, ErrorCode code,
    Map<String, String> parameters) {

    return new BadRequestException(message, code, parameters);
  }

  public static NotFoundException notFound(String message, ErrorCode code,
    Map<String, String> parameters) {

    return new NotFoundException(message, code, parameters);
  }

  public static ValidationException validationError(String message, ErrorCode code,
    Map<String, String> parameters) {

    return new ValidationException(message, code, parameters);
  }

}

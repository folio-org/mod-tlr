package org.folio.controller;

import java.util.List;

import org.folio.domain.dto.Error;
import org.folio.domain.dto.Errors;
import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;
import org.folio.exception.ApiException;
import org.folio.exception.BadRequestException;
import org.folio.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Errors> handleBadRequestException(BadRequestException e) {
    return handleApiException(e, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Errors> handleValidationException(ValidationException e) {
    return handleApiException(e, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  private ResponseEntity<Errors> handleApiException(ApiException e, HttpStatus httpStatus) {
    return buildSingleErrorResponseEntity(httpStatus,
      buildError(e, e.getCode(), e.getParameters()));
  }

  private static ResponseEntity<Errors> buildSingleErrorResponseEntity(
    HttpStatusCode httpStatusCode, Error error) {

    return buildResponseEntity(httpStatusCode, new Errors()
      .errors(List.of(error))
      .totalRecords(1));
  }

  private static ResponseEntity<Errors> buildResponseEntity(HttpStatusCode status,
    Errors errorResponse) {

    return ResponseEntity.status(status).body(errorResponse);
  }

  private static Error buildError(Exception e, ErrorCode code, List<Parameter> parameters) {
    return new Error(e.getMessage())
      .code(code.getValue())
      .parameters(parameters);
  }
}

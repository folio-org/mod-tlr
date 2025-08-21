package org.folio.controller;

import java.util.List;
import java.util.Map;

import org.folio.domain.dto.Error;
import org.folio.domain.dto.Errors;
import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;
import org.folio.exception.ApiException;
import org.folio.exception.BadRequestException;
import org.folio.exception.NotFoundException;
import org.folio.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.log4j.Log4j2;

@RestControllerAdvice
@Log4j2
public class ApiErrorHandler {

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Errors> handleBadRequestException(BadRequestException e) {
    return handleApiException(e, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Errors> handleNotFoundException(NotFoundException e) {
    return handleApiException(e, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Errors> handleValidationException(ValidationException e) {
    return handleApiException(e, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  // Catches validation errors from @Valid annotations
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Errors> handleValidationError(MethodArgumentNotValidException e) {
    logException(e);
    return buildSingleErrorResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY,
      buildError(e, ErrorCode.METHOD_ARGUMENT_NOT_VALID));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Errors> handleAnyException(Exception e) {
    logException(e);
    return buildSingleErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR,
      buildError(e, ErrorCode.INTERNAL_SERVER_ERROR));
  }

  private ResponseEntity<Errors> handleApiException(ApiException e, HttpStatus httpStatus) {
    logException(e);
    return buildSingleErrorResponseEntity(httpStatus,
      buildError(e, e.getCode(), e.getParameters()));
  }

  private static ResponseEntity<Errors> buildSingleErrorResponseEntity(
    HttpStatusCode httpStatusCode, Error error) {

    Errors errorResponse = new Errors()
      .errors(List.of(error))
      .totalRecords(1);

    return ResponseEntity.status(httpStatusCode)
      .body(errorResponse);
  }

  private static Error buildError(Exception e, ErrorCode code) {
    return buildError(e, code, null);
  }

  private static Error buildError(Exception e, ErrorCode code, Map<String, String> parameters) {
    List<Parameter> params = null;
    if (parameters != null) {
      params = parameters.entrySet()
        .stream()
        .map(entry -> new Parameter().key(entry.getKey()).value(entry.getValue()))
        .toList();
    }

    return new Error(e.getMessage())
      .code(code.getValue())
      .type(e.getClass().getSimpleName())
      .parameters(params);
  }

  private static void logException(Exception e) {
    log.error("logException:: handling {}", e.getClass().getSimpleName(), e);
  }

}

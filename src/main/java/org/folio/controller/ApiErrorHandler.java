package org.folio.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.folio.domain.dto.Error;
import org.folio.domain.dto.Errors;
import org.folio.domain.dto.Parameter;
import org.folio.domain.type.ErrorCode;
import org.folio.exception.ApiException;
import org.folio.exception.BadRequestException;
import org.folio.exception.NotFoundException;
import org.folio.exception.ValidationException;
import org.folio.spring.exception.FolioContextExecutionException;
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
    return handleApiException(e, HttpStatus.UNPROCESSABLE_CONTENT);
  }

  // Catches validation errors from @Valid annotations
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Errors> handleValidationError(MethodArgumentNotValidException e) {
    logException(e);
    return buildSingleErrorResponseEntity(HttpStatus.UNPROCESSABLE_CONTENT,
      buildError(e, ErrorCode.METHOD_ARGUMENT_NOT_VALID));
  }

  @ExceptionHandler(FolioContextExecutionException.class)
  public ResponseEntity<Errors> handleFolioContextExecutionException(FolioContextExecutionException e) {
    Throwable throwableToHandle = Optional.ofNullable(e.getCause()).orElse(e);
    logException(throwableToHandle);

    return buildSingleErrorResponseEntity(HttpStatus.UNPROCESSABLE_CONTENT,
      buildError(throwableToHandle, ErrorCode.METHOD_ARGUMENT_NOT_VALID));
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

  private static Error buildError(Throwable t, ErrorCode code) {
    return buildError(t, code, null);
  }

  private static Error buildError(Throwable t, ErrorCode code, Map<String, String> parameters) {
    List<Parameter> params = null;
    if (parameters != null) {
      params = parameters.entrySet()
        .stream()
        .map(entry -> new Parameter().key(entry.getKey()).value(entry.getValue()))
        .toList();
    }

    return new Error(t.getMessage())
      .code(code.getValue())
      .type(t.getClass().getSimpleName())
      .parameters(params);
  }

  private static void logException(Throwable t) {
    log.error("logException:: handling {}", t.getClass().getSimpleName(), t);
  }

}

package org.folio.controller;

import java.util.List;

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

@RestControllerAdvice
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
    return buildSingleErrorResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY,
      buildError(e, ErrorCode.METHOD_ARGUMENT_NOT_VALID));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Errors> handleAnyException(Exception e) {
    return buildSingleErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR,
      buildError(e, ErrorCode.INTERNAL_SERVER_ERROR));
  }

  private ResponseEntity<Errors> handleApiException(ApiException e, HttpStatus httpStatus) {
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

  private static Error buildError(Exception e, ErrorCode code, List<Parameter> parameters) {
    return new Error(e.getMessage())
      .code(code.getValue())
      .type(e.getClass().getSimpleName())
      .parameters(parameters);
  }
}

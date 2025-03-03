package org.folio.controller;

import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.exception.HttpFailureFeignException;
import org.folio.rest.resource.EcsLoansApi;
import org.folio.service.CheckOutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class EcsLoansController implements EcsLoansApi {

  private final CheckOutService checkOutService;

  @Override
  public ResponseEntity<CheckOutResponse> checkOutByBarcode(CheckOutRequest checkOutRequest) {
    return ResponseEntity.ok(checkOutService.checkOut(checkOutRequest));
  }

  @ExceptionHandler(HttpFailureFeignException.class)
  public ResponseEntity<String> handleFeignException(HttpFailureFeignException e) {
    log.warn("handleFeignException:: forwarding error response with status {} from {}",
      e::getStatusCode, e::getUrl);
    return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBody());
  }

}

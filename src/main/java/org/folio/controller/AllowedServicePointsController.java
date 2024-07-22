package org.folio.controller;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.RequestOperation;
import org.folio.rest.resource.AllowedServicePointsApi;
import org.folio.service.AllowedServicePointsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class AllowedServicePointsController implements AllowedServicePointsApi {

  private final AllowedServicePointsService allowedServicePointsService;

  @Override
  public ResponseEntity<AllowedServicePointsResponse> getAllowedServicePoints(String operation,
    UUID patronGroupId, UUID instanceId, UUID requestId) {

    log.debug("getAllowedServicePoints:: params: operation={}, patronGroupId={}, instanceId={}, " +
        "requestId={}", operation, patronGroupId, instanceId, requestId);

    RequestOperation requestOperation = Optional.ofNullable(operation)
      .map(String::toUpperCase)
      .map(RequestOperation::valueOf)
      .orElse(null);

    if (validateAllowedServicePointsRequest(requestOperation, patronGroupId, instanceId, requestId)) {
      return ResponseEntity.status(OK).body(allowedServicePointsService.getAllowedServicePoints(
        requestOperation, patronGroupId.toString(), instanceId.toString()));
    } else {
      return ResponseEntity.status(UNPROCESSABLE_ENTITY).build();
    }
  }

  private static boolean validateAllowedServicePointsRequest(RequestOperation operation,
    UUID patronGroupId, UUID instanceId, UUID requestId) {

    log.debug("validateAllowedServicePointsRequest:: parameters operation: {}, requesterId: {}, " +
      "instanceId: {}, requestId: {}", operation, patronGroupId, instanceId, requestId);

    boolean allowedCombinationOfParametersDetected = false;

    List<String> errors = new ArrayList<>();

    if (operation == RequestOperation.CREATE && patronGroupId != null && instanceId != null &&
      requestId == null) {

      log.info("validateAllowedServicePointsRequest:: TLR request creation case");
      allowedCombinationOfParametersDetected = true;
    }

    if (!allowedCombinationOfParametersDetected) {
      String errorMessage = "Invalid combination of query parameters";
      errors.add(errorMessage);
    }

    if (!errors.isEmpty()) {
      String errorMessage = String.join(" ", errors);
      log.error("validateRequest:: allowed service points request failed: {}", errorMessage);
      return false;
    }

    return true;
  }

}

package org.folio.controller;

import static org.folio.domain.dto.RequestOperation.CREATE;
import static org.folio.domain.dto.RequestOperation.REPLACE;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.AllowedServicePointsRequest;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.RequestOperation;
import org.folio.rest.resource.AllowedServicePointsApi;
import org.folio.service.impl.ItemLevelServicePointServiceImpl;
import org.folio.service.impl.TitleLevelServicePointServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class AllowedServicePointsController implements AllowedServicePointsApi {

  private final ItemLevelServicePointServiceImpl itemLevelSpService;
  private final TitleLevelServicePointServiceImpl titleLevelSpService;

  @Override
  public ResponseEntity<AllowedServicePointsResponse> getAllowedServicePoints(String operation,
    UUID requesterId, UUID instanceId, UUID requestId, UUID itemId) {

    log.info("getAllowedServicePoints:: params: operation={}, requesterId={}, instanceId={}, " +
      "requestId={}, itemId={}", operation, requesterId, instanceId, requestId, itemId);

    AllowedServicePointsRequest request = new AllowedServicePointsRequest(
      operation, requesterId, instanceId, requestId, itemId);

    if (validateAllowedServicePointsRequest(request)) {
      var response = getAllowedServicePointsResponse(request);
      return ResponseEntity.status(OK).body(response);
    } else {
      return ResponseEntity.status(UNPROCESSABLE_ENTITY).build();
    }
  }

  private AllowedServicePointsResponse getAllowedServicePointsResponse(
    AllowedServicePointsRequest request) {
    return request.isForTitleLevelRequest() ?
      titleLevelSpService.getAllowedServicePoints(request)
      : itemLevelSpService.getAllowedServicePoints(request);
  }

  private static boolean validateAllowedServicePointsRequest(AllowedServicePointsRequest request) {
    final RequestOperation operation = request.getOperation();
    final String requesterId = request.getRequesterId();
    final String instanceId = request.getInstanceId();
    final String requestId = request.getRequestId();
    final String itemId = request.getItemId();

    boolean allowedCombinationOfParametersDetected = false;

    List<String> errors = new ArrayList<>();

    if (operation == CREATE && requesterId != null && instanceId != null &&
      itemId == null && requestId == null) {

      log.info("validateAllowedServicePointsRequest:: TLR request creation case");
      allowedCombinationOfParametersDetected = true;
    }

    if (operation == CREATE && requesterId != null && instanceId == null &&
      itemId != null && requestId == null) {

      log.info("validateAllowedServicePointsRequest:: ILR request creation case");
      allowedCombinationOfParametersDetected = true;
    }

    if (operation == REPLACE && requesterId == null && instanceId == null &&
      itemId == null && requestId != null) {

      log.info("validateAllowedServicePointsRequest:: request replacement case");
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

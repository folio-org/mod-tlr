package org.folio.controller;

import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.folio.domain.dto.AllowedServicePointsResponse;
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
  public ResponseEntity<AllowedServicePointsResponse> getAllowedServicePoints(UUID requesterId,
    UUID instanceId) {

    log.debug("getAllowedServicePoints:: params: requesterId={}, instanceId={}", requesterId,
      instanceId);

    return ResponseEntity.status(OK).body(allowedServicePointsService.getAllowedServicePoints(
      requesterId.toString(), instanceId.toString()));
  }

}

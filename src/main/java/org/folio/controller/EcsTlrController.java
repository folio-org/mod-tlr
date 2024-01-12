package org.folio.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.rest.resource.TlrApi;
import org.folio.service.EcsTlrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class EcsTlrController implements TlrApi {

  private final EcsTlrService ecsTlrService;

  @Override
  public ResponseEntity<EcsTlr> getEcsTlrById(UUID requestId) {
    log.debug("getEcsTlrById:: parameters id: {}", requestId);

    return ecsTlrService.get(requestId)
      .map(ResponseEntity.status(OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<EcsTlr> postEcsTlr(EcsTlr ecsTlr) {
    log.debug("postEcsTlr:: parameters ecsTlr: {}", ecsTlr);

    try {
      return new ResponseEntity<>(ecsTlrService.post(ecsTlr), CREATED);
    } catch (Exception e) {
      log.error("postEcsTlr:: unexpected error: {}", e.getMessage());
      return new ResponseEntity<>(ecsTlr, INTERNAL_SERVER_ERROR);
    }
  }
}

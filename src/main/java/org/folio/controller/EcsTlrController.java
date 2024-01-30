package org.folio.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.rest.resource.TlrApi;
import org.folio.service.EcsTlrService;
import org.springframework.http.HttpStatus;
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
    log.debug("getEcsTlrById:: parameters requestId: {}", requestId);

    return ecsTlrService.get(requestId)
      .map(ResponseEntity.status(OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<EcsTlr> postEcsTlr(EcsTlr ecsTlr) {
    log.debug("postEcsTlr:: parameters ecsTlr: {}", () -> ecsTlr);

    return ResponseEntity.status(CREATED).body(ecsTlrService.create(ecsTlr));
  }

  @Override
  public ResponseEntity<Void> putEcsTlrById(UUID requestId, EcsTlr ecsTlr) {
    log.debug("putEcsTlrById:: parameters requestId: {}, ecsTlr: {}", () -> requestId, () -> ecsTlr);
    boolean requestUpdated = ecsTlrService.update(requestId, ecsTlr);
    HttpStatus httpStatus = requestUpdated ? NO_CONTENT : NOT_FOUND;

    return ResponseEntity.status(httpStatus).build();
  }

  @Override
  public ResponseEntity<Void> deleteEcsTlrById(UUID requestId) {
    log.debug("deleteEcsTlrById:: parameters requestId: {}", requestId);
    boolean requestDeleted = ecsTlrService.delete(requestId);
    HttpStatus httpStatus = requestDeleted ? NO_CONTENT : NOT_FOUND;

    return ResponseEntity.status(httpStatus).build();
  }
}

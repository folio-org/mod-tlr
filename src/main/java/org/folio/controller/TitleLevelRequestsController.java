package org.folio.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.folio.domain.dto.TitleLevelRequest;
import org.folio.rest.resource.TitleLevelRequestsApi;
import org.folio.service.TitleLevelRequestsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class TitleLevelRequestsController implements TitleLevelRequestsApi {

  private final TitleLevelRequestsService titleLevelRequestsService;

  @Override
  public ResponseEntity<TitleLevelRequest> getTitleLevelRequestById(UUID requestId) {
    log.debug("getTitleLevelRequest:: parameters id: {}", requestId);

    return titleLevelRequestsService.get(requestId)
      .map(ResponseEntity.status(OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<TitleLevelRequest> postTitleLevelRequest(
    TitleLevelRequest titleLevelRequest) {

    log.debug("postTitleLevelRequest:: parameters titleLevelRequest: {}", titleLevelRequest);

    try {
      return new ResponseEntity<>(titleLevelRequestsService.post(titleLevelRequest), CREATED);
    } catch (Exception e) {
      log.error("postTitleLevelRequest:: unexpected error: {}", e.getMessage());
      return new ResponseEntity<>(titleLevelRequest, INTERNAL_SERVER_ERROR);
    }
  }
}

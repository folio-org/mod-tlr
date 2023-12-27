package org.folio.mr.controller;

import java.util.UUID;

import org.folio.mr.domain.dto.TitleLevelRequest;
import org.folio.mr.rest.resource.TitleLevelRequestsApi;
import org.folio.mr.service.TitleLevelRequestsService;
import org.springframework.http.HttpStatus;
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
  public ResponseEntity<TitleLevelRequest> get(UUID requestId) {
    log.debug("get:: parameters id: {}", requestId);
    return titleLevelRequestsService.get(requestId)
      .map(ResponseEntity.status(HttpStatus.OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }
}

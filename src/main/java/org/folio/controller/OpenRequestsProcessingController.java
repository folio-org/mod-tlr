package org.folio.controller;

import org.folio.service.OpenRequestsProcessingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequiredArgsConstructor
@Log4j2
public class OpenRequestsProcessingController {

  private final OpenRequestsProcessingService openRequestsProcessingService;

  @PostMapping(value = "/title-level-requests-processing")
  public void processOpenRequests() {
    openRequestsProcessingService.processOpenRequests();
  }

}
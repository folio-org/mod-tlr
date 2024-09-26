package org.folio.service.impl;

import org.folio.service.OpenRequestsProcessingService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class OpenRequestsProcessingServiceImpl implements OpenRequestsProcessingService {

  @Override
  public void processOpenRequests() {
    log.info("processOpenRequests:: start");
  }

}

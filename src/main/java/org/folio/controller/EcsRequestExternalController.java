package org.folio.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.rest.resource.EcsRequestExternalApi;
import org.folio.service.EcsTlrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class EcsRequestExternalController implements EcsRequestExternalApi {

  private final EcsTlrService ecsTlrService;
  private final EcsTlrMapper ecsTlrMapper;

  @Override
  public ResponseEntity<EcsTlr> postEcsRequestExternal(EcsRequestExternal ecsRequestExternal) {
    log.info("postEcsRequestExternal:: parameters ecsRequestExternal: {}", ecsRequestExternal);
    EcsTlr ecsTlr = ecsTlrMapper.mapEcsRequestExternalToEcsTlr(ecsRequestExternal);
    // List of request types to be tried in their respective order
    EcsTlr.RequestTypeEnum[] requestTypes = {
      EcsTlr.RequestTypeEnum.PAGE,
      EcsTlr.RequestTypeEnum.RECALL,
      EcsTlr.RequestTypeEnum.HOLD
    };

    for (EcsTlr.RequestTypeEnum requestType : requestTypes) {
      EcsTlr ecsTlrResult = ecsTlrService.create(ecsTlr.requestType(requestType));
      if (ecsTlrResult != null) {
        log.info("postEcsRequestExternal:: resultEcsTlr for {} request type is {}", requestType, ecsTlrResult);
        return ResponseEntity.status(CREATED).body(ecsTlrResult);
      }
    }

    log.warn("postEcsRequestExternal:: failed to create EcsTlr for any request type");
    return ResponseEntity.status(BAD_REQUEST).build();
  }
}

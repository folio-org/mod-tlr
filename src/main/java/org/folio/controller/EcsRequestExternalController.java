package org.folio.controller;

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
    // Try to create ECS TLR for 'PAGE' type
    EcsTlr resultEcsTlr = ecsTlrService.create(ecsTlr.requestType(EcsTlr.RequestTypeEnum.PAGE));
    if (resultEcsTlr != null) {
      log.info("postEcsRequestExternal:: resultEcsTlr for PAGE request type is {}", resultEcsTlr);
      return ResponseEntity.status(CREATED).body(resultEcsTlr);
    }
    // Fallback to 'RECALL' type if 'PAGE' type failed
    resultEcsTlr = ecsTlrService.create(ecsTlr.requestType(EcsTlr.RequestTypeEnum.RECALL));
    if (resultEcsTlr != null) {
      log.info("postEcsRequestExternal:: resultEcsTlr for RECALL request type is {}", resultEcsTlr);
      return ResponseEntity.status(CREATED).body(resultEcsTlr);
    }
    // Fallback to 'HOLD' type if both 'PAGE' and 'RECALL' types failed
    resultEcsTlr = ecsTlrService.create(ecsTlr.requestType(EcsTlr.RequestTypeEnum.HOLD));
    log.info("postEcsRequestExternal:: resultEcsTlr for HOLD request type is {}", resultEcsTlr);

    return ResponseEntity.status(CREATED).body(resultEcsTlr);
  }
}

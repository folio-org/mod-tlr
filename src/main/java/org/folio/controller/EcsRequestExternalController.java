package org.folio.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.mapper.ExternalEcsRequestMapper;
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

  private static final EcsTlr.RequestTypeEnum[] ORDERED_REQUEST_TYPES = {
    EcsTlr.RequestTypeEnum.PAGE,
    EcsTlr.RequestTypeEnum.RECALL,
    EcsTlr.RequestTypeEnum.HOLD
  };

  private final EcsTlrService ecsTlrService;
  private final ExternalEcsRequestMapper externalEcsRequestMapper;

  @Override
  public ResponseEntity<EcsTlr> postEcsRequestExternal(EcsRequestExternal ecsRequestExternal) {
    log.info("postEcsRequestExternal:: creating external ECS request, instance {}, " +
        "item {}, requester {}", ecsRequestExternal.getInstanceId(),
      ecsRequestExternal.getItemId(), ecsRequestExternal.getRequesterId());

    EcsTlr ecsTlrDto = externalEcsRequestMapper.mapEcsRequestExternalToEcsTlr(ecsRequestExternal);

    for (EcsTlr.RequestTypeEnum requestType: ORDERED_REQUEST_TYPES) {
      EcsTlr ecsTlr = ecsTlrService.create(ecsTlrDto.requestType(requestType));
      if (ecsTlr != null) {
        log.info("postEcsRequestExternal:: created ECS request {}, request type is {}",
          ecsTlr.getId(), requestType);
        return ResponseEntity.status(CREATED).body(ecsTlr);
      }
    }

    log.warn("postEcsRequestExternal:: failed to create external ECS request");
    return ResponseEntity.status(BAD_REQUEST).build();
  }
}

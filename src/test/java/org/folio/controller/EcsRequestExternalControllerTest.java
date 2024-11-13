package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.Date;
import java.util.UUID;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.service.EcsTlrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcsRequestExternalControllerTest {
  @Mock
  private EcsTlrService ecsTlrService;
  @Spy
  private final EcsTlrMapper ecsTlrMapper = new EcsTlrMapperImpl();
  @InjectMocks
  private EcsRequestExternalController ecsRequestExternalController;

  @Test
  void ecsRequestExternalShouldSuccessfullyBeCreatedForRecallRequestType() {
    EcsRequestExternal ecsRequestExternal = new EcsRequestExternal()
      .instanceId(UUID.randomUUID().toString())
      .requesterId(UUID.randomUUID().toString())
      .requestLevel(EcsRequestExternal.RequestLevelEnum.TITLE)
      .fulfillmentPreference(EcsRequestExternal.FulfillmentPreferenceEnum.HOLD_SHELF)
      .requestDate(new Date());
    EcsTlr recallEcsTlr = new EcsTlr().requestType(EcsTlr.RequestTypeEnum.RECALL);

    when(ecsTlrService.create(any(EcsTlr.class)))
      .thenReturn(null)
      .thenReturn(recallEcsTlr);

    var response = ecsRequestExternalController.postEcsRequestExternal(ecsRequestExternal);

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(recallEcsTlr, response.getBody());
  }
}
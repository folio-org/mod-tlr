package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.Date;
import java.util.UUID;

import org.folio.domain.dto.EcsRequestExternal;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.mapper.ExternalEcsRequestMapper;
import org.folio.domain.mapper.ExternalEcsRequestMapperImpl;
import org.folio.exception.RequestCreatingException;
import org.folio.service.EcsTlrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcsRequestExternalControllerTest {
  private static final String ERROR_MESSAGE = "Error message";
  @Mock
  private EcsTlrService ecsTlrService;
  @Spy
  private final ExternalEcsRequestMapper externalEcsRequestMapper =
    new ExternalEcsRequestMapperImpl();
  @InjectMocks
  private EcsRequestExternalController ecsRequestExternalController;

  @Test
  void ecsRequestExternalShouldSuccessfullyBeCreatedForPageRequestType() {
    EcsRequestExternal ecsRequestExternal = new EcsRequestExternal()
      .instanceId(UUID.randomUUID().toString())
      .requesterId(UUID.randomUUID().toString())
      .requestLevel(EcsRequestExternal.RequestLevelEnum.TITLE)
      .fulfillmentPreference(EcsRequestExternal.FulfillmentPreferenceEnum.HOLD_SHELF)
      .requestDate(new Date());
    EcsTlr pageEcsTlr = new EcsTlr().requestType(EcsTlr.RequestTypeEnum.PAGE);

    when(ecsTlrService.create(any(EcsTlr.class)))
      .thenReturn(pageEcsTlr);

    var response = ecsRequestExternalController.postEcsRequestExternal(ecsRequestExternal);

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(pageEcsTlr, response.getBody());
  }

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
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE))
      .thenReturn(recallEcsTlr);

    var response = ecsRequestExternalController.postEcsRequestExternal(ecsRequestExternal);

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(recallEcsTlr, response.getBody());
  }

  @Test
  void ecsRequestExternalShouldSuccessfullyBeCreatedForHoldRequestType() {
    EcsRequestExternal ecsRequestExternal = new EcsRequestExternal()
      .instanceId(UUID.randomUUID().toString())
      .requesterId(UUID.randomUUID().toString())
      .requestLevel(EcsRequestExternal.RequestLevelEnum.TITLE)
      .fulfillmentPreference(EcsRequestExternal.FulfillmentPreferenceEnum.HOLD_SHELF)
      .requestDate(new Date());
    EcsTlr holdEcsTlr = new EcsTlr().requestType(EcsTlr.RequestTypeEnum.HOLD);

    when(ecsTlrService.create(any(EcsTlr.class)))
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE))
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE))
      .thenReturn(holdEcsTlr);

    var response = ecsRequestExternalController.postEcsRequestExternal(ecsRequestExternal);

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(holdEcsTlr, response.getBody());
  }

  @Test
  void ecsRequestExternalShouldReturnBadRequest() {
    EcsRequestExternal ecsRequestExternal = new EcsRequestExternal()
      .instanceId(UUID.randomUUID().toString())
      .requesterId(UUID.randomUUID().toString())
      .requestLevel(EcsRequestExternal.RequestLevelEnum.TITLE)
      .fulfillmentPreference(EcsRequestExternal.FulfillmentPreferenceEnum.HOLD_SHELF)
      .requestDate(new Date());

    when(ecsTlrService.create(any(EcsTlr.class)))
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE))
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE))
      .thenThrow(new RequestCreatingException(ERROR_MESSAGE));

    assertEquals(BAD_REQUEST, ecsRequestExternalController.postEcsRequestExternal(
      ecsRequestExternal).getStatusCode());
  }
}

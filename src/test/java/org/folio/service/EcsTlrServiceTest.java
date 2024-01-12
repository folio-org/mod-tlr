package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.EcsTlrServiceImpl;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcsTlrServiceTest {

  @InjectMocks
  private EcsTlrServiceImpl ecsTlrService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Mock
  private EcsTlrMapper ecsTlrMapper;

  @Test
  void getById() {
    ecsTlrService.get(any());
    verify(ecsTlrRepository).findById(any());
  }

  @Test
  void postEcsTlr() {
    var instanceId = UUID.randomUUID();
    var requesterId = UUID.randomUUID();
    var pickupServicePointId = UUID.randomUUID();
    var requestType = EcsTlr.RequestTypeEnum.PAGE;
    var requestLevel = EcsTlr.RequestLevelEnum.TITLE;
    var fulfillmentPreference = EcsTlr.FulfillmentPreferenceEnum.HOLD_SHELF;
    var requestExpirationDate = DateTime.now().toDate();
    var patronComments = "Test comment";

    var mockEcsTlrEntity = new EcsTlrEntity();
    mockEcsTlrEntity.setInstanceId(instanceId);
    mockEcsTlrEntity.setRequesterId(requesterId);
    mockEcsTlrEntity.setRequestType(requestType.getValue());
    mockEcsTlrEntity.setRequestLevel(requestLevel.getValue());
    mockEcsTlrEntity.setRequestExpirationDate(requestExpirationDate.toString());
    mockEcsTlrEntity.setPatronComments(patronComments);
    mockEcsTlrEntity.setFulfillmentPreference(fulfillmentPreference.getValue());
    mockEcsTlrEntity.setPickupServicePointId(pickupServicePointId);

    var mockRequest = new EcsTlr();
    mockRequest.setInstanceId(instanceId.toString());
    mockRequest.setRequesterId(requesterId.toString());
    mockRequest.setRequestType(requestType);
    mockRequest.setRequestLevel(requestLevel);
    mockRequest.setRequestExpirationDate(requestExpirationDate);
    mockRequest.setPatronComments(patronComments);
    mockRequest.setFulfillmentPreference(fulfillmentPreference);
    mockRequest.setPickupServicePointId(pickupServicePointId.toString());

    when(ecsTlrMapper.mapDtoToEntity(any(EcsTlr.class))).thenReturn(mockEcsTlrEntity);
    when(ecsTlrMapper.mapEntityToDto(any(EcsTlrEntity.class))).thenReturn(mockRequest);
    when(ecsTlrRepository.save(any(EcsTlrEntity.class))).thenReturn(mockEcsTlrEntity);

    var postEcsTlr = ecsTlrService.post(mockRequest);

    assertEquals(instanceId.toString(), postEcsTlr.getInstanceId());
    assertEquals(requesterId.toString(), postEcsTlr.getRequesterId());
    assertEquals(requestType, postEcsTlr.getRequestType());
    assertEquals(requestExpirationDate, postEcsTlr.getRequestExpirationDate());
    assertEquals(patronComments, postEcsTlr.getPatronComments());
    assertEquals(fulfillmentPreference, postEcsTlr.getFulfillmentPreference());
    assertEquals(pickupServicePointId.toString(), postEcsTlr.getPickupServicePointId());
  }
}

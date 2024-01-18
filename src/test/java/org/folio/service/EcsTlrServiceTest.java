package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.EcsTlrServiceImpl;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcsTlrServiceTest {

  @InjectMocks
  private EcsTlrServiceImpl ecsTlrService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Spy
  private final EcsTlrMapper ecsTlrMapper = new EcsTlrMapperImpl();

  @Test
  void getById() {
    ecsTlrService.get(any());
    verify(ecsTlrRepository).findById(any());
  }

  @Test
  void postEcsTlr() {
    var id = UUID.randomUUID();
    var instanceId = UUID.randomUUID();
    var requesterId = UUID.randomUUID();
    var pickupServicePointId = UUID.randomUUID();
    var requestType = EcsTlr.RequestTypeEnum.PAGE;
    var requestLevel = EcsTlr.RequestLevelEnum.TITLE;
    var fulfillmentPreference = EcsTlr.FulfillmentPreferenceEnum.HOLD_SHELF;
    var requestExpirationDate = DateTime.now().toDate();
    var patronComments = "Test comment";

    var mockEcsTlrEntity = new EcsTlrEntity();
    mockEcsTlrEntity.setId(id);
    mockEcsTlrEntity.setInstanceId(instanceId);
    mockEcsTlrEntity.setRequesterId(requesterId);
    mockEcsTlrEntity.setRequestType(requestType.toString());
    mockEcsTlrEntity.setRequestLevel(requestLevel.getValue());
    mockEcsTlrEntity.setRequestExpirationDate(requestExpirationDate);
    mockEcsTlrEntity.setPatronComments(patronComments);
    mockEcsTlrEntity.setFulfillmentPreference(fulfillmentPreference.getValue());
    mockEcsTlrEntity.setPickupServicePointId(pickupServicePointId);

    var ecsTlr = new EcsTlr();
    ecsTlr.setId(id.toString());
    ecsTlr.setInstanceId(instanceId.toString());
    ecsTlr.setRequesterId(requesterId.toString());
    ecsTlr.setRequestType(requestType);
    ecsTlr.setRequestLevel(requestLevel);
    ecsTlr.setRequestExpirationDate(requestExpirationDate);
    ecsTlr.setPatronComments(patronComments);
    ecsTlr.setFulfillmentPreference(fulfillmentPreference);
    ecsTlr.setPickupServicePointId(pickupServicePointId.toString());

    when(ecsTlrRepository.save(any(EcsTlrEntity.class))).thenReturn(mockEcsTlrEntity);
    var postEcsTlr = ecsTlrService.post(ecsTlr);

    assertEquals(id.toString(), postEcsTlr.getId());
    assertEquals(instanceId.toString(), postEcsTlr.getInstanceId());
    assertEquals(requesterId.toString(), postEcsTlr.getRequesterId());
    assertEquals(requestType, postEcsTlr.getRequestType());
    assertEquals(requestExpirationDate, postEcsTlr.getRequestExpirationDate());
    assertEquals(patronComments, postEcsTlr.getPatronComments());
    assertEquals(fulfillmentPreference, postEcsTlr.getFulfillmentPreference());
    assertEquals(pickupServicePointId.toString(), postEcsTlr.getPickupServicePointId());
  }
}

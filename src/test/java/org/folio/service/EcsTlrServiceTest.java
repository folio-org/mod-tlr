package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.domain.strategy.TenantPickingStrategy;
import org.folio.exception.TenantPickingException;
import org.folio.exception.TenantScopedExecutionException;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.EcsTlrServiceImpl;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock
  private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock
  private TenantPickingStrategy tenantPickingStrategy;
  @Spy
  private final EcsTlrMapper ecsTlrMapper = new EcsTlrMapperImpl();

  @Test
  void getById() {
    ecsTlrService.get(any());
    verify(ecsTlrRepository).findById(any());
  }

  @Test
  void ecsTlrShouldBeCreatedThenUpdatedAndDeleted() {
    var id = UUID.randomUUID();
    var instanceId = UUID.randomUUID();
    var requesterId = UUID.randomUUID();
    var pickupServicePointId = UUID.randomUUID();
    var requestType = EcsTlr.RequestTypeEnum.PAGE;
    var requestLevel = EcsTlr.RequestLevelEnum.TITLE;
    var fulfillmentPreference = EcsTlr.FulfillmentPreferenceEnum.HOLD_SHELF;
    var requestExpirationDate = DateTime.now().plusDays(7).toDate();
    var requestDate = DateTime.now().toDate();
    var patronComments = "Test comment";

    var mockEcsTlrEntity = new EcsTlrEntity();
    mockEcsTlrEntity.setId(id);
    mockEcsTlrEntity.setInstanceId(instanceId);
    mockEcsTlrEntity.setRequesterId(requesterId);
    mockEcsTlrEntity.setRequestType(requestType.toString());
    mockEcsTlrEntity.setRequestLevel(requestLevel.getValue());
    mockEcsTlrEntity.setRequestExpirationDate(requestExpirationDate);
    mockEcsTlrEntity.setRequestDate(requestDate);
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
    ecsTlr.setRequestDate(requestDate);
    ecsTlr.setPatronComments(patronComments);
    ecsTlr.setFulfillmentPreference(fulfillmentPreference);
    ecsTlr.setPickupServicePointId(pickupServicePointId.toString());

    when(ecsTlrRepository.save(any(EcsTlrEntity.class))).thenReturn(mockEcsTlrEntity);
    when(tenantPickingStrategy.findTenants(any(String.class)))
      .thenReturn(List.of("random-tenant"));
    when(tenantScopedExecutionService.execute(any(String.class), any()))
      .thenReturn(new Request().id(UUID.randomUUID().toString()));
    var postEcsTlr = ecsTlrService.create(ecsTlr);

    assertEquals(id.toString(), postEcsTlr.getId());
    assertEquals(instanceId.toString(), postEcsTlr.getInstanceId());
    assertEquals(requesterId.toString(), postEcsTlr.getRequesterId());
    assertEquals(requestType, postEcsTlr.getRequestType());
    assertEquals(requestExpirationDate, postEcsTlr.getRequestExpirationDate());
    assertEquals(requestDate, postEcsTlr.getRequestDate());
    assertEquals(patronComments, postEcsTlr.getPatronComments());
    assertEquals(fulfillmentPreference, postEcsTlr.getFulfillmentPreference());
    assertEquals(pickupServicePointId.toString(), postEcsTlr.getPickupServicePointId());

    when(ecsTlrRepository.existsById(any(UUID.class))).thenReturn(true);
    assertTrue(ecsTlrService.update(id, ecsTlr));
    assertTrue(ecsTlrService.delete(id));

    when(ecsTlrRepository.existsById(any(UUID.class))).thenReturn(false);
    assertFalse(ecsTlrService.update(id, ecsTlr));
    assertFalse(ecsTlrService.delete(id));
  }

  @Test
  void canNotCreateRemoteRequestWhenFailedToPickTenant() {
    when(tenantPickingStrategy.findTenants(any(String.class)))
      .thenReturn(Collections.emptyList());
    String instanceId = UUID.randomUUID().toString();
    EcsTlr ecsTlr = new EcsTlr().instanceId(instanceId);

    TenantPickingException exception = assertThrows(TenantPickingException.class,
      () -> ecsTlrService.create(ecsTlr));

    assertEquals("Failed to find tenants for instance " + instanceId, exception.getMessage());
  }

  @Test
  void canCreateRemoteRequestOnlyForSecondTenantId() {
    String instanceId = UUID.randomUUID().toString();
    EcsTlr ecsTlr = new EcsTlr()
      .instanceId(instanceId)
      .id(UUID.randomUUID().toString());
    String firstTenantId = UUID.randomUUID().toString();
    String secondTenantId = UUID.randomUUID().toString();
    String thirdTenantId = UUID.randomUUID().toString();

    List<String> mockTenantIds = List.of(
      firstTenantId, secondTenantId, thirdTenantId);
    when(tenantPickingStrategy.findTenants(any(String.class)))
      .thenReturn(mockTenantIds);
    when(tenantScopedExecutionService.execute(any(), any()))
      .thenThrow(new TenantScopedExecutionException(new RuntimeException("Test failure"), firstTenantId))
      .thenReturn(new Request().id(UUID.randomUUID().toString()))
      .thenReturn(new Request().id(UUID.randomUUID().toString()));
    ecsTlrService.create(ecsTlr);

    ArgumentCaptor<EcsTlrEntity> captor = ArgumentCaptor.forClass(EcsTlrEntity.class);
    verify(ecsTlrRepository, times(1)).save(captor.capture());
    assertEquals(secondTenantId, captor.getValue().getSecondaryRequestTenantId());
  }
}

package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.exception.TenantPickingException;
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
  private UserService userService;
  @Mock
  private RequestService requestService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Mock
  private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock
  private TenantService tenantService;
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
    when(tenantService.getBorrowingTenant())
      .thenReturn(Optional.of("borrowing_tenant"));
    when(tenantService.getLendingTenant(instanceId.toString()))
      .thenReturn(Optional.of("lending_tenant"));
    doNothing().when(userService)
      .createShadowUser(any(EcsTlr.class), any(String.class), any(String.class));
    when(requestService.createPrimaryRequest(any(Request.class), any(String.class)))
      .thenReturn(new Request());
    when(requestService.createSecondaryRequest(any(Request.class), any(String.class)))
      .thenReturn(new Request());

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
  void canNotCreateRemoteRequestWhenFailedToGetBorrowingTenantId() {
    when(tenantService.getBorrowingTenant())
      .thenReturn(Optional.empty());
    String instanceId = UUID.randomUUID().toString();
    EcsTlr ecsTlr = new EcsTlr().instanceId(instanceId);

    TenantPickingException exception = assertThrows(TenantPickingException.class,
      () -> ecsTlrService.create(ecsTlr));

    assertEquals("Failed to extract borrowing tenant ID from token", exception.getMessage());
  }

  @Test
  void canNotCreateRemoteRequestWhenFailedToGetLendingTenantId() {
    String instanceId = UUID.randomUUID().toString();
    when(tenantService.getBorrowingTenant())
      .thenReturn(Optional.of("borrowing_tenant"));
    when(tenantService.getLendingTenant(instanceId))
      .thenReturn(Optional.empty());
    EcsTlr ecsTlr = new EcsTlr().instanceId(instanceId);

    TenantPickingException exception = assertThrows(TenantPickingException.class,
      () -> ecsTlrService.create(ecsTlr));

    assertEquals("Failed to pick lending tenant for instance " + instanceId, exception.getMessage());
  }
}

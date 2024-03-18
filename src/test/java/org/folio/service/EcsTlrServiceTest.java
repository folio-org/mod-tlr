package org.folio.service;

import static java.util.Collections.emptyList;
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
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.RequestWrapper;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.entity.EcsTlrSettingsEntity;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.mapper.EcsTlrMapperImpl;
import org.folio.domain.mapper.EcsTlrSettingsMapper;
import org.folio.domain.mapper.EcsTlrSettingsMapperImpl;
import org.folio.exception.TenantPickingException;
import org.folio.repository.EcsTlrRepository;
import org.folio.repository.EcsTlrSettingsRepository;
import org.folio.service.impl.EcsTlrServiceImpl;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
  private EcsTlrSettingsRepository ecsTlrSettingsRepository;
  @Mock
  private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock
  private TenantService tenantService;
  @Spy
  private final EcsTlrMapper ecsTlrMapper = new EcsTlrMapperImpl();
  @Spy
  private final EcsTlrSettingsMapper ecsTlrSettingsMapper = new EcsTlrSettingsMapperImpl();

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
    var borrowingTenant = "borrowing-tenant";
    var lendingTenant = "lending-tenant";

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
    when(tenantService.getBorrowingTenant(any(EcsTlr.class)))
      .thenReturn(Optional.of(borrowingTenant));
    when(tenantService.getLendingTenants(any(EcsTlr.class)))
      .thenReturn(List.of(lendingTenant));
    when(requestService.createPrimaryRequest(any(Request.class), any(String.class)))
      .thenReturn(new RequestWrapper(new Request(), borrowingTenant));
    when(requestService.createSecondaryRequest(any(Request.class), any(String.class), any()))
      .thenReturn(new RequestWrapper(new Request(), borrowingTenant));

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
  void canNotCreateEcsTlrWhenFailedToGetBorrowingTenantId() {
    String instanceId = UUID.randomUUID().toString();
    EcsTlr ecsTlr = new EcsTlr().instanceId(instanceId);
    when(tenantService.getBorrowingTenant(ecsTlr))
      .thenReturn(Optional.empty());

    TenantPickingException exception = assertThrows(TenantPickingException.class,
      () -> ecsTlrService.create(ecsTlr));

    assertEquals("Failed to get borrowing tenant", exception.getMessage());
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToGetLendingTenants() {
    String instanceId = UUID.randomUUID().toString();
    EcsTlr ecsTlr = new EcsTlr().instanceId(instanceId);
    when(tenantService.getBorrowingTenant(ecsTlr))
      .thenReturn(Optional.of("borrowing_tenant"));
    when(tenantService.getLendingTenants(ecsTlr))
      .thenReturn(emptyList());

    TenantPickingException exception = assertThrows(TenantPickingException.class,
      () -> ecsTlrService.create(ecsTlr));

    assertEquals("Failed to find lending tenants for instance " + instanceId, exception.getMessage());
  }

  @Test
  void getEcsTlrSettings() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(new EcsTlrSettingsEntity(UUID.randomUUID(), true))));

    Optional<EcsTlrSettings> ecsTlrSettings = ecsTlrService.getEcsTlrSettings();
    verify(ecsTlrSettingsRepository).findAll(any(PageRequest.class));
    assertTrue(ecsTlrSettings.isPresent());
    assertTrue(ecsTlrSettings.map(EcsTlrSettings::getEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void getEcsTlrSettingsWithEmptyValue() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<EcsTlrSettings> ecsTlrSettings = ecsTlrService.getEcsTlrSettings();
    verify(ecsTlrSettingsRepository).findAll(any(PageRequest.class));
    assertFalse(ecsTlrSettings.isPresent());
  }

  @Test
  void updateEcsTlrSettings() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(new EcsTlrSettingsEntity(UUID.randomUUID(), true))));

    Optional<EcsTlrSettingsEntity> ecsTlrSettings = ecsTlrService.updateEcsTlrSettings(new EcsTlrSettings());
    verify(ecsTlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(ecsTlrSettingsRepository, times(1)).save(any(EcsTlrSettingsEntity.class));
    assertTrue(ecsTlrSettings.isPresent());
    assertTrue(ecsTlrSettings.map(EcsTlrSettingsEntity::isEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void cannotUpdateEcsTlrSettingsIfItDoesNotExist() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<EcsTlrSettingsEntity> ecsTlrSettings = ecsTlrService.updateEcsTlrSettings(new EcsTlrSettings());
    verify(ecsTlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(ecsTlrSettingsRepository, times(0)).save(any(EcsTlrSettingsEntity.class));
    assertFalse(ecsTlrSettings.isPresent());
  }
}

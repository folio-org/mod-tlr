package org.folio.service;

import static java.util.UUID.randomUUID;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.INTERMEDIATE;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.PRIMARY;
import static org.folio.domain.dto.Request.EcsRequestPhaseEnum.SECONDARY;
import static org.folio.domain.dto.Request.FulfillmentPreferenceEnum.DELIVERY;
import static org.folio.domain.dto.Request.FulfillmentPreferenceEnum.HOLD_SHELF;
import static org.folio.domain.dto.Request.StatusEnum.CLOSED_CANCELLED;
import static org.folio.support.Constants.INTERIM_SERVICE_POINT_ID;
import static org.folio.util.TestUtils.buildEvent;
import static org.folio.util.TestUtils.randomId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.folio.domain.dto.Request;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.RequestEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.folio.support.KafkaEvent.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestEventHandlerTest {
  private static final UUID PRIMARY_REQUEST_ID = randomUUID();
  private static final UUID SECONDARY_REQUEST_ID = PRIMARY_REQUEST_ID;
  private static final UUID INTERMEDIATE_REQUEST_ID = PRIMARY_REQUEST_ID;
  private static final String PRIMARY_REQUEST_TENANT_ID = "primary_tenant";
  private static final String SECONDARY_REQUEST_TENANT_ID = "secondary_tenant";
  private static final String INTERMEDIATE_REQUEST_TENANT_ID = "intermediate_tenant";

  @Mock
  private DcbService dcbService;
  @Mock
  RequestService requestService;
  @Mock
  private EcsTlrRepository ecsTlrRepository;
  @Mock
  private ServicePointService servicePointService;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private CloningService<ServicePoint> servicePointCloningService;

  @InjectMocks
  private RequestEventHandler handler;

  @Captor
  ArgumentCaptor<Request> requestCaptor;

  @Test
  void secondaryRequestInterimPickupServicePointShouldNotBeUpdatedUponPrimaryRequestUpdate() {
    EcsTlrEntity ecsTlr = buildEcsTlr();

    Request primaryRequest = new Request()
      .id(PRIMARY_REQUEST_ID.toString())
      .ecsRequestPhase(PRIMARY)
      .status(Request.StatusEnum.OPEN_IN_TRANSIT)
      .fulfillmentPreference(DELIVERY)
      .deliveryAddressTypeId(randomId())
      .requestExpirationDate(new Date());

    Request secondaryRequest = new Request()
      .id(SECONDARY_REQUEST_ID.toString())
      .ecsRequestPhase(SECONDARY)
      .fulfillmentPreference(HOLD_SHELF)
      .pickupServicePointId(INTERIM_SERVICE_POINT_ID);

    when(ecsTlrRepository.findBySecondaryRequestId(PRIMARY_REQUEST_ID))
      .thenReturn(Optional.of(ecsTlr));

    when(requestService.getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID))
      .thenReturn(secondaryRequest);

    KafkaEvent<Request> event = buildRequestUpdateEvent(primaryRequest, primaryRequest,
      PRIMARY_REQUEST_TENANT_ID);

    handler.handle(event);

    verifyNoInteractions(dcbService);
    verify(ecsTlrRepository).findBySecondaryRequestId(SECONDARY_REQUEST_ID);
    verify(requestService).getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID);
    verify(requestService).updateRequestInStorage(requestCaptor.capture(), eq(SECONDARY_REQUEST_TENANT_ID));

    Request updatedSecondaryRequest = requestCaptor.getValue();
    assertThat(updatedSecondaryRequest.getRequestExpirationDate(),
      is(primaryRequest.getRequestExpirationDate()));
    assertThat(updatedSecondaryRequest.getPickupServicePointId(), is(INTERIM_SERVICE_POINT_ID));
    assertThat(updatedSecondaryRequest.getFulfillmentPreference(), is(HOLD_SHELF));
  }

  @Test
  void primaryRequestChangesArePropagatedToSecondaryAndIntermediateRequest() {
    EcsTlrEntity ecsTlr = buildEcsTlr();
    ecsTlr.setIntermediateRequestId(INTERMEDIATE_REQUEST_ID);
    ecsTlr.setIntermediateRequestTenantId(INTERMEDIATE_REQUEST_TENANT_ID);

    Request primaryRequest = new Request()
      .id(PRIMARY_REQUEST_ID.toString())
      .ecsRequestPhase(PRIMARY)
      .status(Request.StatusEnum.OPEN_IN_TRANSIT)
      .fulfillmentPreference(HOLD_SHELF)
      .pickupServicePointId(randomId())
      .requestExpirationDate(new Date());

    Request secondaryRequest = new Request()
      .id(SECONDARY_REQUEST_ID.toString())
      .ecsRequestPhase(SECONDARY)
      .status(Request.StatusEnum.OPEN_IN_TRANSIT)
      .fulfillmentPreference(DELIVERY)
      .pickupServicePointId(null)
      .requestExpirationDate(null);

    Request intermediateRequest = new Request()
      .id(INTERMEDIATE_REQUEST_ID.toString())
      .ecsRequestPhase(INTERMEDIATE)
      .status(Request.StatusEnum.OPEN_IN_TRANSIT)
      .fulfillmentPreference(HOLD_SHELF)
      .pickupServicePointId(randomId())
      .requestExpirationDate(new Date());

    ServicePoint mockServicePoint = new ServicePoint().id(primaryRequest.getPickupServicePointId());
    when(servicePointService.find(primaryRequest.getPickupServicePointId()))
      .thenReturn(mockServicePoint);
    when(servicePointCloningService.clone(mockServicePoint))
      .thenReturn(mockServicePoint);
    when(executionService.executeSystemUserScoped(any(String.class), any(Callable.class)))
      .thenAnswer(invocation -> invocation.getArgument(1, Callable.class).call());
    when(ecsTlrRepository.findBySecondaryRequestId(PRIMARY_REQUEST_ID))
      .thenReturn(Optional.of(ecsTlr));
    when(requestService.getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID))
      .thenReturn(secondaryRequest);
    when(requestService.getRequestFromStorage(INTERMEDIATE_REQUEST_ID.toString(), INTERMEDIATE_REQUEST_TENANT_ID))
      .thenReturn(intermediateRequest);

    KafkaEvent<Request> event = buildRequestUpdateEvent(primaryRequest, primaryRequest,
      PRIMARY_REQUEST_TENANT_ID);

    handler.handle(event);

    verifyNoInteractions(dcbService);
    verify(ecsTlrRepository).findBySecondaryRequestId(SECONDARY_REQUEST_ID);
    verify(requestService).getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID);
    verify(requestService).getRequestFromStorage(INTERMEDIATE_REQUEST_ID.toString(), INTERMEDIATE_REQUEST_TENANT_ID);

    verify(requestService).updateRequestInStorage(requestCaptor.capture(), eq(SECONDARY_REQUEST_TENANT_ID));
    Request updatedSecondaryRequest = requestCaptor.getValue();
    assertThat(updatedSecondaryRequest.getRequestExpirationDate(),
      is(primaryRequest.getRequestExpirationDate()));
    assertThat(updatedSecondaryRequest.getFulfillmentPreference(), is(HOLD_SHELF));
    assertThat(updatedSecondaryRequest.getPickupServicePointId(),
      is(primaryRequest.getPickupServicePointId()));

    verify(requestService).updateRequestInStorage(requestCaptor.capture(), eq(INTERMEDIATE_REQUEST_TENANT_ID));
    Request updatedIntermediateRequest = requestCaptor.getValue();
    assertThat(updatedIntermediateRequest.getRequestExpirationDate(),
      is(primaryRequest.getRequestExpirationDate()));
    assertThat(updatedIntermediateRequest.getFulfillmentPreference(), is(HOLD_SHELF));
    assertThat(updatedIntermediateRequest.getPickupServicePointId(),
      is(primaryRequest.getPickupServicePointId()));
  }

  @Test
  void secondaryRequestCanceledWhenPrimaryHoldRequestCanceled() {
    EcsTlrEntity ecsTlr = buildEcsTlr();
    Request primaryRequest = new Request()
      .id(PRIMARY_REQUEST_ID.toString())
      .ecsRequestPhase(PRIMARY)
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.HOLD)
      .status(CLOSED_CANCELLED);
    Request secondaryRequest = new Request()
      .id(SECONDARY_REQUEST_ID.toString())
      .ecsRequestPhase(SECONDARY)
      .status(Request.StatusEnum.OPEN_IN_TRANSIT);
    when(ecsTlrRepository.findBySecondaryRequestId(PRIMARY_REQUEST_ID))
      .thenReturn(Optional.of(ecsTlr));
    when(requestService.getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID))
      .thenReturn(secondaryRequest);

    KafkaEvent<Request> event = buildRequestUpdateEvent(primaryRequest, primaryRequest,
      PRIMARY_REQUEST_TENANT_ID);
    handler.handle(event);

    verifyNoInteractions(dcbService);
    verify(ecsTlrRepository).findBySecondaryRequestId(SECONDARY_REQUEST_ID);
    verify(requestService).getRequestFromStorage(SECONDARY_REQUEST_ID.toString(), SECONDARY_REQUEST_TENANT_ID);
    verify(requestService).updateRequestInStorage(requestCaptor.capture(), eq(SECONDARY_REQUEST_TENANT_ID));
    Request updatedSecondaryRequest = requestCaptor.getValue();
    assertThat(updatedSecondaryRequest.getStatus(), is(CLOSED_CANCELLED));
  }

  private static KafkaEvent<Request> buildRequestUpdateEvent(Request oldVersion,
    Request newVersion, String tenantId) {

    return buildEvent(tenantId, EventType.UPDATED, oldVersion, newVersion);
  }

  private static EcsTlrEntity buildEcsTlr() {
    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setId(randomUUID());
    ecsTlr.setPrimaryRequestId(PRIMARY_REQUEST_ID);
    ecsTlr.setPrimaryRequestTenantId(PRIMARY_REQUEST_TENANT_ID);
    ecsTlr.setSecondaryRequestId(SECONDARY_REQUEST_ID);
    ecsTlr.setSecondaryRequestTenantId(SECONDARY_REQUEST_TENANT_ID);

    return ecsTlr;
  }
}

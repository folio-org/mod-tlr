package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.folio.support.CqlQuery.exactMatch;
import static org.folio.support.CqlQuery.exactMatchAny;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.StaffSlip;
import org.folio.domain.dto.StaffSlipItem;
import org.folio.domain.dto.StaffSlipRequest;
import org.folio.domain.dto.Tenant;
import org.folio.service.ConsortiaService;
import org.folio.service.InventoryService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.CqlQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PickSlipsServiceTest {

  private static final String SERVICE_POINT_ID = "6582fb37-9748-40a0-a0be-51efd151fa53";

  @Mock
  private LocationService locationService;
  @Mock
  private InventoryService inventoryService;
  @Mock
  private RequestService requestService;
  @Mock
  private ConsortiaService consortiaService;
  @Mock
  private SystemUserScopedExecutionService executionService;

  @InjectMocks
  private PickSlipsService pickSlipsService;

  @BeforeEach
  public void setup() {
    // Bypass the use of system user and return the result of Callable immediately
    when(executionService.executeSystemUserScoped(any(), any()))
      .thenAnswer(invocation -> invocation.getArgument(1, Callable.class).call());
  }

  @Test
  void pickSlipsAreBuiltSuccessfully() {
    Location mockLocation = new Location()
      .id(randomUUID().toString())
      .name("test location")
      .discoveryDisplayName("location display name");

    Item mockItem = new Item()
      .id(randomUUID().toString())
      .barcode("item_barcode")
      .status(new ItemStatus().name(PAGED))
      .enumeration("enum")
      .volume("vol")
      .chronology("chrono")
      .yearCaption(Set.of("2000", "2001"))
      .copyNumber("copy")
      .numberOfPieces("1")
      .displaySummary("summary")
      .descriptionOfPieces("description")
      .effectiveLocationId(mockLocation.getId())
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
        .callNumber("CN")
        .prefix("PFX")
        .suffix("SFX"));

    Request mockRequest = new Request()
      .id(randomUUID().toString())
      .itemId(mockItem.getId())
      .requestLevel(Request.RequestLevelEnum.ITEM)
      .requestType(PAGE)
      .pickupServicePointId(randomUUID().toString())
      .requesterId(randomUUID().toString())
      .requestDate(new Date())
      .requestExpirationDate(new Date())
      .holdShelfExpirationDate(new Date())
      .cancellationAdditionalInformation("cancellation info")
      .cancellationReasonId(randomUUID().toString())
      .deliveryAddressTypeId(randomUUID().toString())
      .patronComments("comment");

    CqlQuery itemCommonQuery = exactMatchAny("status.name", List.of("Paged"));
    CqlQuery requestCommonQuery = exactMatchAny("requestType", List.of("Page"))
      .and(exactMatchAny("status", List.of("Open - Not yet filled")));

    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("consortium")));
    when(locationService.findLocations(exactMatch("primaryServicePoint", SERVICE_POINT_ID)))
      .thenReturn(List.of(mockLocation));
    when(inventoryService.findItems(itemCommonQuery, "effectiveLocationId", List.of(mockLocation.getId())))
      .thenReturn(List.of(mockItem));
    when(requestService.getRequestsFromStorage(requestCommonQuery, "itemId", List.of(mockItem.getId())))
      .thenReturn(List.of(mockRequest));

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);
    assertThat(staffSlips, hasSize(1));

    StaffSlip actualPickSlip = staffSlips.iterator().next();
    assertThat(actualPickSlip.getCurrentDateTime(), notNullValue());

    StaffSlipItem pickSlipItem = actualPickSlip.getItem();
    assertThat(pickSlipItem.getBarcode(), is("item_barcode"));
    assertThat(pickSlipItem.getStatus(), is("Paged"));
    assertThat(pickSlipItem.getEnumeration(), is("enum"));
    assertThat(pickSlipItem.getVolume(), is("vol"));
    assertThat(pickSlipItem.getChronology(), is("chrono"));
    assertThat(pickSlipItem.getYearCaption(), oneOf("2000; 2001", "2001; 2000"));
    assertThat(pickSlipItem.getCopy(), is("copy"));
    assertThat(pickSlipItem.getNumberOfPieces(), is("1"));
    assertThat(pickSlipItem.getDisplaySummary(), is("summary"));
    assertThat(pickSlipItem.getDescriptionOfPieces(), is("description"));
    assertThat(pickSlipItem.getEffectiveLocationSpecific(), is("test location"));
    assertThat(pickSlipItem.getEffectiveLocationDiscoveryDisplayName(), is("location display name"));
    assertThat(pickSlipItem.getCallNumber(), is("CN"));
    assertThat(pickSlipItem.getCallNumberPrefix(), is("PFX"));
    assertThat(pickSlipItem.getCallNumberSuffix(), is("SFX"));

    StaffSlipRequest pickSlipRequest = actualPickSlip.getRequest();
    assertThat(pickSlipRequest.getRequestId(), is(UUID.fromString(mockRequest.getId())));
    assertThat(pickSlipRequest.getRequestDate(), is(mockRequest.getRequestDate()));
    assertThat(pickSlipRequest.getRequestExpirationDate(), is(mockRequest.getRequestExpirationDate()));
    assertThat(pickSlipRequest.getHoldShelfExpirationDate(), is(mockRequest.getHoldShelfExpirationDate()));
    assertThat(pickSlipRequest.getAdditionalInfo(), is("cancellation info"));
    assertThat(pickSlipRequest.getPatronComments(), is("comment"));
  }

  @Test
  void noConsortiumTenantsAreFound() {
    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(emptyList());

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);

    assertThat(staffSlips, empty());
    verifyNoInteractions(locationService, inventoryService, requestService, executionService);
  }

  @Test
  void noLocationsAreFound() {
    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("test_tenant")));
    when(locationService.findLocations(any(CqlQuery.class)))
      .thenReturn(emptyList());

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);

    assertThat(staffSlips, empty());
    verifyNoInteractions(inventoryService, requestService);
  }

  @Test
  void noItemsAreFound() {
    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("test_tenant")));
    when(locationService.findLocations(any(CqlQuery.class)))
      .thenReturn(List.of(new Location().id(randomUUID().toString())));
    when(inventoryService.findItems(any(), any(), any()))
      .thenReturn(emptyList());

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);

    assertThat(staffSlips, empty());
    verifyNoInteractions(requestService);
  }

  @Test
  void noRequestsAreFound() {
    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("test_tenant")));
    when(locationService.findLocations(any(CqlQuery.class)))
      .thenReturn(List.of(new Location()));
    when(inventoryService.findItems(any(), any(), any()))
      .thenReturn(List.of(new Item()));
    when(requestService.getRequestsFromStorage(any(), any(), any()))
      .thenReturn(emptyList());

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);

    assertThat(staffSlips, empty());
  }
}
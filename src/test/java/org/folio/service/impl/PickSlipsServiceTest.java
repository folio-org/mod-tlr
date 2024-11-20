package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;
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
import java.util.stream.Stream;

import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Contributor;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchHolding;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.StaffSlip;
import org.folio.domain.dto.StaffSlipItem;
import org.folio.domain.dto.StaffSlipRequest;
import org.folio.domain.dto.StaffSlipRequester;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserPersonalAddressesInner;
import org.folio.service.AddressTypeService;
import org.folio.service.ConsortiaService;
import org.folio.service.DepartmentService;
import org.folio.service.InventoryService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.SearchService;
import org.folio.service.ServicePointService;
import org.folio.service.UserGroupService;
import org.folio.service.UserService;
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
  @Mock
  private UserService userService;
  @Mock
  private UserGroupService userGroupService;
  @Mock
  private DepartmentService departmentService;
  @Mock
  private AddressTypeService addressTypeService;
  @Mock
  private SearchService searchService;
  @Mock
  private ServicePointService servicePointService;

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
      .id(randomId())
      .name("Test location")
      .discoveryDisplayName("Location display name")
      .libraryId(randomId())
      .campusId(randomId())
      .institutionId(randomId())
      .primaryServicePoint(randomUUID());

    SearchItem mockSearchItem = new SearchItem()
      .id(randomId())
      .effectiveLocationId(mockLocation.getId())
      .tenantId("consortium");

    SearchHolding mockSearchHolding = new SearchHolding()
      .id(randomId())
      .tenantId("consortium");

    SearchInstance mockSearchInstance = new SearchInstance()
      .id(randomId())
      .title("Test title")
      .items(List.of(mockSearchItem))
      .holdings(List.of(mockSearchHolding))
      .contributors(List.of(
        new Contributor().name("First, Author").primary(true),
        new Contributor().name("Second, Author").primary(false)
      ));

    Item mockItem = new Item()
      .id(mockSearchItem.getId())
      .holdingsRecordId(mockSearchHolding.getId())
      .barcode("item_barcode")
      .status(new ItemStatus().name(PAGED))
      .materialTypeId(randomId())
      .permanentLoanTypeId(randomId())
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

    MaterialType mockMaterialType = new MaterialType()
      .id(mockItem.getMaterialTypeId())
      .name("Material type");

    LoanType mockLoanType = new LoanType()
      .id(mockItem.getPermanentLoanTypeId())
      .name("Loan type");

    Library mockLibrary = new Library()
      .id(mockLocation.getLibraryId())
      .name("Library");

    Campus mockCampus = new Campus()
      .id(mockLocation.getCampusId())
      .name("Campus");

    Institution mockInstitution = new Institution()
      .id(mockLocation.getInstitutionId())
      .name("Institution");

    ServicePoint mockPrimaryServicePoint = new ServicePoint()
      .id(mockLocation.getPrimaryServicePoint().toString())
      .name("Primary service point");
    ServicePoint mockPickupServicePoint = new ServicePoint()
      .id(randomId())
      .name("Pickup service point");

    AddressType mockPrimaryAddressType = new AddressType()
      .id(randomId())
      .addressType("Primary address type");
    AddressType mockDeliveryAddressType = new AddressType()
      .id(randomId())
      .addressType("Delivery address type");

    Request mockRequest = new Request()
      .id(randomId())
      .itemId(mockItem.getId())
      .requestLevel(Request.RequestLevelEnum.ITEM)
      .requestType(PAGE)
      .pickupServicePointId(mockPickupServicePoint.getId())
      .requesterId(randomId())
      .requestDate(new Date())
      .requestExpirationDate(new Date())
      .holdShelfExpirationDate(new Date())
      .deliveryAddressTypeId(mockDeliveryAddressType.getId())
      .patronComments("comment");

    Collection<Department> mockDepartments = List.of(
      new Department().id(randomId()).name("First department"),
      new Department().id(randomId()).name("Second department"));
    Set<String> mockDepartmentIds = mockDepartments.stream()
      .map(Department::getId)
      .collect(toSet());

    UserGroup mockUserGroup = new UserGroup()
      .id(randomId())
      .group("User group");

    User mockRequester = new User()
      .id(mockRequest.getRequesterId())
      .barcode("Requester barcode")
      .patronGroup(mockUserGroup.getId())
      .departments(mockDepartmentIds)
      .personal(new UserPersonal()
        .firstName("First name")
        .middleName("Middle name")
        .lastName("Last name")
        .preferredFirstName("Preferred first name")
        .addresses(List.of(
          new UserPersonalAddressesInner()
            .id(randomId())
            .primaryAddress(true)
            .addressTypeId(mockPrimaryAddressType.getId())
            .addressLine1("Primary address line 1")
            .addressLine2("Primary address line 2")
            .city("Primary address city")
            .region("Primary address region")
            .postalCode("Primary address zip code")
            .countryId("US"),
          new UserPersonalAddressesInner()
            .id(randomId())
            .primaryAddress(false)
            .addressTypeId(mockRequest.getDeliveryAddressTypeId())
            .addressLine1("Delivery address line 1")
            .addressLine2("Delivery address line 2")
            .city("Delivery address city")
            .region("Delivery address region")
            .postalCode("Delivery address zip code")
            .countryId("US")
        )));

    Set<String> departmentIds = mockDepartments.stream()
      .map(Department::getId)
      .collect(toSet());

    Set<String> addressTypeIds = Stream.of(mockPrimaryAddressType, mockDeliveryAddressType)
      .map(AddressType::getId)
      .collect(toSet());

    CqlQuery searchInstancesCommonQuery = CqlQuery.exactMatchAny("item.status.name", List.of("Paged"));
    CqlQuery requestCommonQuery = exactMatchAny("requestType", List.of("Page"))
      .and(exactMatchAny("status", List.of("Open - Not yet filled")));

    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("consortium")));
    when(locationService.findLocations(exactMatch("primaryServicePoint", SERVICE_POINT_ID)))
      .thenReturn(List.of(mockLocation));
    when(searchService.searchInstances(searchInstancesCommonQuery, "item.effectiveLocationId",
      Set.of(mockLocation.getId())))
      .thenReturn(List.of(mockSearchInstance));
    when(requestService.getRequestsFromStorage(requestCommonQuery, "itemId", Set.of(mockItem.getId())))
      .thenReturn(List.of(mockRequest));
    when(inventoryService.findItems(Set.of(mockItem.getId())))
      .thenReturn(List.of(mockItem));
    when(inventoryService.findMaterialTypes(Set.of(mockMaterialType.getId())))
      .thenReturn(List.of(mockMaterialType));
    when(inventoryService.findLoanTypes(Set.of(mockLoanType.getId())))
      .thenReturn(List.of(mockLoanType));
    when(inventoryService.findLibraries(Set.of(mockLibrary.getId())))
      .thenReturn(List.of(mockLibrary));
    when(inventoryService.findCampuses(Set.of(mockCampus.getId())))
      .thenReturn(List.of(mockCampus));
    when(inventoryService.findInstitutions(Set.of(mockInstitution.getId())))
      .thenReturn(List.of(mockInstitution));
    when(servicePointService.find(Set.of(mockPrimaryServicePoint.getId())))
      .thenReturn(List.of(mockPrimaryServicePoint));
    when(servicePointService.find(Set.of(mockPickupServicePoint.getId())))
      .thenReturn(List.of(mockPickupServicePoint));
    when(userService.find(Set.of(mockRequester.getId())))
      .thenReturn(List.of(mockRequester));
    when(userGroupService.find(Set.of(mockUserGroup.getId())))
      .thenReturn(List.of(mockUserGroup));
    when(departmentService.findDepartments(departmentIds))
      .thenReturn(mockDepartments);
    when(addressTypeService.findAddressTypes(addressTypeIds))
      .thenReturn(List.of(mockPrimaryAddressType, mockDeliveryAddressType));

    Collection<StaffSlip> staffSlips = pickSlipsService.getStaffSlips(SERVICE_POINT_ID);
    assertThat(staffSlips, hasSize(1));

    StaffSlip actualPickSlip = staffSlips.iterator().next();
    assertThat(actualPickSlip.getCurrentDateTime(), notNullValue());

    StaffSlipItem pickSlipItem = actualPickSlip.getItem();
    assertThat(pickSlipItem.getBarcode(), is("item_barcode"));
    assertThat(pickSlipItem.getStatus(), is("Paged"));
    assertThat(pickSlipItem.getMaterialType(), is("Material type"));
    assertThat(pickSlipItem.getLoanType(), is("Loan type"));
    assertThat(pickSlipItem.getEnumeration(), is("enum"));
    assertThat(pickSlipItem.getVolume(), is("vol"));
    assertThat(pickSlipItem.getChronology(), is("chrono"));
    assertThat(pickSlipItem.getYearCaption(), oneOf("2000; 2001", "2001; 2000"));
    assertThat(pickSlipItem.getCopy(), is("copy"));
    assertThat(pickSlipItem.getNumberOfPieces(), is("1"));
    assertThat(pickSlipItem.getDisplaySummary(), is("summary"));
    assertThat(pickSlipItem.getDescriptionOfPieces(), is("description"));
    assertThat(pickSlipItem.getTitle(), is("Test title"));
    assertThat(pickSlipItem.getPrimaryContributor(), is("First, Author"));
    assertThat(pickSlipItem.getAllContributors(), is("First, Author; Second, Author"));
    assertThat(pickSlipItem.getEffectiveLocationSpecific(), is("Test location"));
    assertThat(pickSlipItem.getEffectiveLocationLibrary(), is("Library"));
    assertThat(pickSlipItem.getEffectiveLocationCampus(), is("Campus"));
    assertThat(pickSlipItem.getEffectiveLocationInstitution(), is("Institution"));
    assertThat(pickSlipItem.getEffectiveLocationPrimaryServicePointName(), is("Primary service point"));
    assertThat(pickSlipItem.getEffectiveLocationDiscoveryDisplayName(), is("Location display name"));
    assertThat(pickSlipItem.getCallNumber(), is("CN"));
    assertThat(pickSlipItem.getCallNumberPrefix(), is("PFX"));
    assertThat(pickSlipItem.getCallNumberSuffix(), is("SFX"));

    StaffSlipRequest pickSlipRequest = actualPickSlip.getRequest();
    assertThat(pickSlipRequest.getRequestId(), is(UUID.fromString(mockRequest.getId())));
    assertThat(pickSlipRequest.getServicePointPickup(), is("Pickup service point"));
    assertThat(pickSlipRequest.getRequestDate(), is(mockRequest.getRequestDate()));
    assertThat(pickSlipRequest.getRequestExpirationDate(), is(mockRequest.getRequestExpirationDate()));
    assertThat(pickSlipRequest.getHoldShelfExpirationDate(), is(mockRequest.getHoldShelfExpirationDate()));
    assertThat(pickSlipRequest.getDeliveryAddressType(), is("Delivery address type"));
    assertThat(pickSlipRequest.getPatronComments(), is("comment"));

    StaffSlipRequester pickSlipRequester = actualPickSlip.getRequester();
    assertThat(pickSlipRequester.getBarcode(), is("Requester barcode"));
    assertThat(pickSlipRequester.getPatronGroup(), is("User group"));
    assertThat(pickSlipRequester.getDepartments(),
      oneOf("First department; Second department", "Second department; First department"));
    assertThat(pickSlipRequester.getFirstName(), is("First name"));
    assertThat(pickSlipRequester.getMiddleName(), is("Middle name"));
    assertThat(pickSlipRequester.getLastName(), is("Last name"));
    assertThat(pickSlipRequester.getPreferredFirstName(), is("Preferred first name"));
    assertThat(pickSlipRequester.getAddressLine1(), is("Delivery address line 1"));
    assertThat(pickSlipRequester.getAddressLine2(), is("Delivery address line 2"));
    assertThat(pickSlipRequester.getCity(), is("Delivery address city"));
    assertThat(pickSlipRequester.getRegion(), is("Delivery address region"));
    assertThat(pickSlipRequester.getPostalCode(), is("Delivery address zip code"));
    assertThat(pickSlipRequester.getCountryId(), is("US"));
    assertThat(pickSlipRequester.getAddressType(), is("Delivery address type"));
    assertThat(pickSlipRequester.getPrimaryAddressLine1(), is("Primary address line 1"));
    assertThat(pickSlipRequester.getPrimaryAddressLine2(), is("Primary address line 2"));
    assertThat(pickSlipRequester.getPrimaryCity(), is("Primary address city"));
    assertThat(pickSlipRequester.getPrimaryStateProvRegion(), is("Primary address region"));
    assertThat(pickSlipRequester.getPrimaryZipPostalCode(), is("Primary address zip code"));
    assertThat(pickSlipRequester.getPrimaryCountry(), is("United States"));
    assertThat(pickSlipRequester.getPrimaryDeliveryAddressType(), is("Primary address type"));
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
      .thenReturn(List.of(new Location().id(randomId())));
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

  private static String randomId() {
    return randomUUID().toString();
  }

}
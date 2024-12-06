package org.folio.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;
import static org.folio.domain.dto.ItemStatus.NameEnum.CHECKED_OUT;
import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.Request.FulfillmentPreferenceEnum.DELIVERY;
import static org.folio.domain.dto.Request.FulfillmentPreferenceEnum.HOLD_SHELF;
import static org.folio.domain.dto.Request.RequestLevelEnum.ITEM;
import static org.folio.domain.dto.Request.RequestLevelEnum.TITLE;
import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.folio.support.CqlQuery.exactMatch;
import static org.folio.support.CqlQuery.exactMatchAny;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.folio.domain.dto.Department;
import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.InstanceContributorsInner;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.Request;
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
import org.folio.service.impl.PickSlipsService;
import org.folio.service.impl.SearchSlipsService;
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
class StaffSlipsServiceTest {

  private static final String SERVICE_POINT_ID = randomId();
  private static final String ITEM_ID = randomId();
  private static final String HOLDING_ID = randomId();
  private static final String INSTANCE_ID = randomId();
  private static final String PICKUP_SERVICE_POINT_ID = randomId();
  private static final String REQUESTER_ID = randomId();
  private static final String DELIVERY_ADDRESS_TYPE_ID = randomId();
  private static final String PRIMARY_ADDRESS_TYPE_ID = randomId();
  private static final Date REQUEST_DATE = new Date();
  private static final Date REQUEST_EXPIRATION_DATE = new Date();
  private static final Date HOLD_SHELF_EXPIRATION_DATE = new Date();

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
  private ServicePointService servicePointService;

  @InjectMocks
  private PickSlipsService pickSlipsService;

  @InjectMocks
  private SearchSlipsService searchSlipsService;

  @BeforeEach
  public void setup() {
    // Bypass the use of system user and return the result of Callable immediately
    when(executionService.executeSystemUserScoped(any(String.class), any(Callable.class)))
      .thenAnswer(invocation -> invocation.getArgument(1, Callable.class).call());
  }

  @Test
  void pickSlipsAreBuiltSuccessfully() {
    Request request = buildRequest(PAGE, ITEM);
    Location location = buildLocation();
    Instance instance = buildInstance(request.getInstanceId());
    HoldingsRecord holding = buildHolding(request.getHoldingsRecordId(), request.getInstanceId(), location.getId());
    Item item = buildItem(PAGED, request.getItemId(), request.getHoldingsRecordId(), location.getId());
    MaterialType materialType = buildMaterialType(item.getMaterialTypeId());
    LoanType loanType = buildLoanType(item.getPermanentLoanTypeId());
    Library library = buildLibrary(location.getLibraryId());
    Campus campus = buildCampus(location.getCampusId());
    Institution institution = buildInstitution(location.getInstitutionId());
    ServicePoint primaryServicePoint = buildPrimaryServicePoint(location.getPrimaryServicePoint().toString());
    ServicePoint pickupServicePoint = buildPickupServicePoint(request.getPickupServicePointId());
    AddressType primaryAddressType = buildPrimaryAddressType();
    AddressType deliveryAddressType = buildDeliveryAddressType();
    Collection<Department> departments = buildDepartments();
    Set<String> departmentIds = departments.stream().map(Department::getId).collect(toSet());
    User requester = buildRequester(request.getRequesterId(), departmentIds);
    UserGroup userGroup = buildUserGroup(requester.getPatronGroup());

    Set<String> addressTypeIds = Stream.of(primaryAddressType, deliveryAddressType)
      .map(AddressType::getId)
      .collect(toSet());
    CqlQuery itemsCommonQuery = CqlQuery.exactMatchAny("status.name", List.of("Paged"));
    CqlQuery requestsCommonQuery = exactMatchAny("requestType", List.of("Page"))
      .and(exactMatchAny("status", List.of("Open - Not yet filled")));

    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("consortium")));
    when(locationService.findLocations(exactMatch("primaryServicePoint", SERVICE_POINT_ID)))
      .thenReturn(List.of(location));
    when(inventoryService.findItems(itemsCommonQuery, "effectiveLocationId", Set.of(location.getId())))
      .thenReturn(List.of(item));
    when(requestService.getRequestsFromStorage(requestsCommonQuery, "itemId", List.of(item.getId())))
      .thenReturn(List.of(request));
    when(inventoryService.findInstances(Set.of(instance.getId())))
      .thenReturn(List.of(instance));
    when(inventoryService.findHoldings(Set.of(holding.getId())))
      .thenReturn(List.of(holding));
    when(inventoryService.findMaterialTypes(Set.of(materialType.getId())))
      .thenReturn(List.of(materialType));
    when(inventoryService.findLoanTypes(Set.of(loanType.getId())))
      .thenReturn(List.of(loanType));
    when(inventoryService.findLibraries(Set.of(library.getId())))
      .thenReturn(List.of(library));
    when(inventoryService.findCampuses(Set.of(campus.getId())))
      .thenReturn(List.of(campus));
    when(inventoryService.findInstitutions(Set.of(institution.getId())))
      .thenReturn(List.of(institution));
    when(servicePointService.find(Set.of(primaryServicePoint.getId())))
      .thenReturn(List.of(primaryServicePoint));
    when(servicePointService.find(Set.of(pickupServicePoint.getId())))
      .thenReturn(List.of(pickupServicePoint));
    when(userService.find(Set.of(requester.getId())))
      .thenReturn(List.of(requester));
    when(userGroupService.find(Set.of(userGroup.getId())))
      .thenReturn(List.of(userGroup));
    when(departmentService.findDepartments(departmentIds))
      .thenReturn(departments);
    when(addressTypeService.findAddressTypes(addressTypeIds))
      .thenReturn(List.of(primaryAddressType, deliveryAddressType));

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
    assertThat(pickSlipRequest.getRequestID(), is(UUID.fromString(request.getId())));
    assertThat(pickSlipRequest.getServicePointPickup(), is("Pickup service point"));
    assertThat(pickSlipRequest.getRequestDate(), is(request.getRequestDate()));
    assertThat(pickSlipRequest.getRequestExpirationDate(), is(request.getRequestExpirationDate()));
    assertThat(pickSlipRequest.getHoldShelfExpirationDate(), is(request.getHoldShelfExpirationDate()));
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
  void searchSlipsAreBuiltSuccessfully() {
    Request requestWithItem = buildRequest(HOLD, ITEM).pickupServicePointId(null);
    Request requestWithoutItem = buildRequest(HOLD, TITLE, null, null, requestWithItem.getInstanceId())
      .fulfillmentPreference(HOLD_SHELF)
      .pickupServicePointId(PICKUP_SERVICE_POINT_ID)
      .deliveryAddressTypeId(null);
    Location location = buildLocation();
    Instance instance = buildInstance(requestWithItem.getInstanceId());
    HoldingsRecord holding = buildHolding(requestWithItem.getHoldingsRecordId(),
      requestWithItem.getInstanceId(), location.getId());
    Item item = buildItem(CHECKED_OUT, requestWithItem.getItemId(),
      requestWithItem.getHoldingsRecordId(), location.getId());
    MaterialType materialType = buildMaterialType(item.getMaterialTypeId());
    LoanType loanType = buildLoanType(item.getPermanentLoanTypeId());
    Library library = buildLibrary(location.getLibraryId());
    Campus campus = buildCampus(location.getCampusId());
    Institution institution = buildInstitution(location.getInstitutionId());
    ServicePoint primaryServicePoint = buildPrimaryServicePoint(location.getPrimaryServicePoint().toString());
    ServicePoint pickupServicePoint = buildPickupServicePoint(requestWithoutItem.getPickupServicePointId());
    AddressType primaryAddressType = buildPrimaryAddressType();
    AddressType deliveryAddressType = buildDeliveryAddressType();
    Collection<Department> departments = buildDepartments();
    Set<String> departmentIds = departments.stream().map(Department::getId).collect(toSet());
    User requester = buildRequester(requestWithItem.getRequesterId(), departmentIds);
    UserGroup userGroup = buildUserGroup(requester.getPatronGroup());

    Set<String> addressTypeIds = Stream.of(primaryAddressType, deliveryAddressType)
      .map(AddressType::getId)
      .collect(toSet());

    CqlQuery requestsCommonQuery = exactMatchAny("requestType", List.of("Hold"))
      .and(exactMatchAny("status", List.of("Open - Not yet filled")));

    CqlQuery holdsWithoutItemQuery = CqlQuery.exactMatch("requestType", HOLD.getValue())
      .and(CqlQuery.exactMatch("requestLevel", TITLE.getValue()))
      .and(CqlQuery.exactMatchAny("status", List.of("Open - Not yet filled")))
      .not(CqlQuery.match("itemId", ""));

    when(consortiaService.getAllConsortiumTenants())
      .thenReturn(List.of(new Tenant().id("consortium")));
    when(locationService.findLocations(exactMatch("primaryServicePoint", SERVICE_POINT_ID)))
      .thenReturn(List.of(location));
    when(inventoryService.findItems(any(CqlQuery.class), anyString(), anySet()))
      .thenReturn(List.of(item));
    when(requestService.getRequestsFromStorage(requestsCommonQuery, "itemId", List.of(item.getId())))
      .thenReturn(List.of(requestWithItem));
    when(requestService.getRequestsFromStorage(holdsWithoutItemQuery))
      .thenReturn(List.of(requestWithoutItem));
    when(inventoryService.findInstances(Set.of(instance.getId())))
      .thenReturn(List.of(instance));
    when(inventoryService.findHoldings(Set.of(holding.getId())))
      .thenReturn(List.of(holding));
    when(inventoryService.findHoldings(CqlQuery.empty(), "instanceId", Set.of(instance.getId())))
      .thenReturn(List.of(holding));
    when(inventoryService.findMaterialTypes(Set.of(materialType.getId())))
      .thenReturn(List.of(materialType));
    when(inventoryService.findLoanTypes(Set.of(loanType.getId())))
      .thenReturn(List.of(loanType));
    when(inventoryService.findLibraries(Set.of(library.getId())))
      .thenReturn(List.of(library));
    when(inventoryService.findCampuses(Set.of(campus.getId())))
      .thenReturn(List.of(campus));
    when(inventoryService.findInstitutions(Set.of(institution.getId())))
      .thenReturn(List.of(institution));
    when(servicePointService.find(Set.of(primaryServicePoint.getId())))
      .thenReturn(List.of(primaryServicePoint));
    when(servicePointService.find(Set.of(pickupServicePoint.getId())))
      .thenReturn(List.of(pickupServicePoint));
    when(userService.find(Set.of(requester.getId())))
      .thenReturn(List.of(requester));
    when(userGroupService.find(Set.of(userGroup.getId())))
      .thenReturn(List.of(userGroup));
    when(departmentService.findDepartments(departmentIds))
      .thenReturn(departments);
    when(addressTypeService.findAddressTypes(addressTypeIds))
      .thenReturn(List.of(primaryAddressType, deliveryAddressType));

    Collection<StaffSlip> staffSlips = searchSlipsService.getStaffSlips(SERVICE_POINT_ID);
    assertThat(staffSlips, hasSize(2));

    StaffSlip searchSlipForRequestWithItem = staffSlips.stream()
      .filter(slip -> slip.getRequest().getRequestID().toString().equals(requestWithItem.getId()))
      .findFirst()
      .orElseThrow();

    StaffSlipItem searchSlipItem = searchSlipForRequestWithItem.getItem();
    assertThat(searchSlipItem.getBarcode(), is("item_barcode"));
    assertThat(searchSlipItem.getStatus(), is("Checked out"));
    assertThat(searchSlipItem.getMaterialType(), is("Material type"));
    assertThat(searchSlipItem.getLoanType(), is("Loan type"));
    assertThat(searchSlipItem.getEnumeration(), is("enum"));
    assertThat(searchSlipItem.getVolume(), is("vol"));
    assertThat(searchSlipItem.getChronology(), is("chrono"));
    assertThat(searchSlipItem.getYearCaption(), oneOf("2000; 2001", "2001; 2000"));
    assertThat(searchSlipItem.getCopy(), is("copy"));
    assertThat(searchSlipItem.getNumberOfPieces(), is("1"));
    assertThat(searchSlipItem.getDisplaySummary(), is("summary"));
    assertThat(searchSlipItem.getDescriptionOfPieces(), is("description"));
    assertThat(searchSlipItem.getTitle(), is("Test title"));
    assertThat(searchSlipItem.getPrimaryContributor(), is("First, Author"));
    assertThat(searchSlipItem.getAllContributors(), is("First, Author; Second, Author"));
    assertThat(searchSlipItem.getEffectiveLocationSpecific(), is("Test location"));
    assertThat(searchSlipItem.getEffectiveLocationLibrary(), is("Library"));
    assertThat(searchSlipItem.getEffectiveLocationCampus(), is("Campus"));
    assertThat(searchSlipItem.getEffectiveLocationInstitution(), is("Institution"));
    assertThat(searchSlipItem.getEffectiveLocationPrimaryServicePointName(), is("Primary service point"));
    assertThat(searchSlipItem.getEffectiveLocationDiscoveryDisplayName(), is("Location display name"));
    assertThat(searchSlipItem.getCallNumber(), is("CN"));
    assertThat(searchSlipItem.getCallNumberPrefix(), is("PFX"));
    assertThat(searchSlipItem.getCallNumberSuffix(), is("SFX"));

    StaffSlipRequester searchSlipWithItemRequester = searchSlipForRequestWithItem.getRequester();
    assertThat(searchSlipWithItemRequester.getAddressLine1(), is("Delivery address line 1"));
    assertThat(searchSlipWithItemRequester.getAddressLine2(), is("Delivery address line 2"));
    assertThat(searchSlipWithItemRequester.getCity(), is("Delivery address city"));
    assertThat(searchSlipWithItemRequester.getRegion(), is("Delivery address region"));
    assertThat(searchSlipWithItemRequester.getPostalCode(), is("Delivery address zip code"));
    assertThat(searchSlipWithItemRequester.getCountryId(), is("US"));
    assertThat(searchSlipWithItemRequester.getAddressType(), is("Delivery address type"));

    assertThat(searchSlipForRequestWithItem.getRequest().getServicePointPickup(), nullValue());
    assertThat(searchSlipForRequestWithItem.getRequest().getDeliveryAddressType(),
      is("Delivery address type"));

    StaffSlip searchSlipForRequestWithoutItem = staffSlips.stream()
      .filter(slip -> slip.getRequest().getRequestID().toString().equals(requestWithoutItem.getId()))
      .findFirst()
      .orElseThrow();

    StaffSlipRequester searchSlipWithoutItemRequester = searchSlipForRequestWithoutItem.getRequester();
    assertThat(searchSlipWithoutItemRequester.getAddressLine1(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getAddressLine2(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getCity(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getRegion(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getPostalCode(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getCountryId(), nullValue());
    assertThat(searchSlipWithoutItemRequester.getAddressType(), nullValue());

    assertThat(searchSlipForRequestWithoutItem.getRequest().getDeliveryAddressType(), nullValue());
    assertThat(searchSlipForRequestWithoutItem.getRequest().getServicePointPickup(),
      is("Pickup service point"));
    assertThat(searchSlipForRequestWithoutItem.getItem(), nullValue());

    Stream.of(searchSlipForRequestWithItem, searchSlipForRequestWithoutItem).forEach(searchSlip -> {
      assertThat(searchSlip.getCurrentDateTime(), notNullValue());

      StaffSlipRequest pickSlipRequest = searchSlip.getRequest();
      assertThat(pickSlipRequest.getRequestDate(), is(REQUEST_DATE));
      assertThat(pickSlipRequest.getRequestExpirationDate(), is(REQUEST_EXPIRATION_DATE));
      assertThat(pickSlipRequest.getHoldShelfExpirationDate(), is(HOLD_SHELF_EXPIRATION_DATE));

      assertThat(pickSlipRequest.getPatronComments(), is("comment"));

      StaffSlipRequester pickSlipRequester = searchSlip.getRequester();
      assertThat(pickSlipRequester.getBarcode(), is("Requester barcode"));
      assertThat(pickSlipRequester.getPatronGroup(), is("User group"));
      assertThat(pickSlipRequester.getDepartments(),
        oneOf("First department; Second department", "Second department; First department"));
      assertThat(pickSlipRequester.getFirstName(), is("First name"));
      assertThat(pickSlipRequester.getMiddleName(), is("Middle name"));
      assertThat(pickSlipRequester.getLastName(), is("Last name"));
      assertThat(pickSlipRequester.getPreferredFirstName(), is("Preferred first name"));
      assertThat(pickSlipRequester.getPrimaryAddressLine1(), is("Primary address line 1"));
      assertThat(pickSlipRequester.getPrimaryAddressLine2(), is("Primary address line 2"));
      assertThat(pickSlipRequester.getPrimaryCity(), is("Primary address city"));
      assertThat(pickSlipRequester.getPrimaryStateProvRegion(), is("Primary address region"));
      assertThat(pickSlipRequester.getPrimaryZipPostalCode(), is("Primary address zip code"));
      assertThat(pickSlipRequester.getPrimaryCountry(), is("United States"));
      assertThat(pickSlipRequester.getPrimaryDeliveryAddressType(), is("Primary address type"));
    });
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

  private static User buildRequester(String id, Set<String> departments) {
    return new User()
      .id(id)
      .barcode("Requester barcode")
      .patronGroup(randomId())
      .departments(departments)
      .personal(new UserPersonal()
        .firstName("First name")
        .middleName("Middle name")
        .lastName("Last name")
        .preferredFirstName("Preferred first name")
        .addresses(List.of(
          new UserPersonalAddressesInner()
            .id(randomId())
            .primaryAddress(true)
            .addressTypeId(PRIMARY_ADDRESS_TYPE_ID)
            .addressLine1("Primary address line 1")
            .addressLine2("Primary address line 2")
            .city("Primary address city")
            .region("Primary address region")
            .postalCode("Primary address zip code")
            .countryId("US"),
          new UserPersonalAddressesInner()
            .id(randomId())
            .primaryAddress(false)
            .addressTypeId(DELIVERY_ADDRESS_TYPE_ID)
            .addressLine1("Delivery address line 1")
            .addressLine2("Delivery address line 2")
            .city("Delivery address city")
            .region("Delivery address region")
            .postalCode("Delivery address zip code")
            .countryId("US")
        )));
  }

  private static UserGroup buildUserGroup(String id) {
    return new UserGroup()
      .id(id)
      .group("User group");
  }

  private static List<Department> buildDepartments() {
    return List.of(
      new Department().id(randomId()).name("First department"),
      new Department().id(randomId()).name("Second department"));
  }

  private static Request buildRequest(Request.RequestTypeEnum type, Request.RequestLevelEnum level) {
    return buildRequest(type, level, ITEM_ID, HOLDING_ID, INSTANCE_ID);
  }

  private static Request buildRequest(Request.RequestTypeEnum type, Request.RequestLevelEnum level,
    String itemId, String holdingId, String instanceId) {

    return new Request()
      .id(randomId())
      .itemId(itemId)
      .holdingsRecordId(holdingId)
      .instanceId(instanceId)
      .requestLevel(level)
      .requestType(type)
      .status(Request.StatusEnum.OPEN_NOT_YET_FILLED)
      .pickupServicePointId(PICKUP_SERVICE_POINT_ID)
      .requesterId(REQUESTER_ID)
      .requestDate(REQUEST_DATE)
      .requestExpirationDate(REQUEST_EXPIRATION_DATE)
      .holdShelfExpirationDate(REQUEST_EXPIRATION_DATE)
      .fulfillmentPreference(DELIVERY)
      .deliveryAddressTypeId(DELIVERY_ADDRESS_TYPE_ID)
      .patronComments("comment");
  }

  private static AddressType buildDeliveryAddressType() {
    return new AddressType().id(DELIVERY_ADDRESS_TYPE_ID).addressType("Delivery address type");
  }

  private static AddressType buildPrimaryAddressType() {
    return new AddressType().id(PRIMARY_ADDRESS_TYPE_ID).addressType("Primary address type");
  }

  private static ServicePoint buildPickupServicePoint(String id) {
    return new ServicePoint().id(id).name("Pickup service point");
  }

  private static ServicePoint buildPrimaryServicePoint(String id) {
    return new ServicePoint().id(id).name("Primary service point");
  }

  private static Institution buildInstitution(String id) {
    return new Institution().id(id).name("Institution");
  }

  private static Campus buildCampus(String id) {
    return new Campus().id(id).name("Campus");
  }

  private static Library buildLibrary(String id) {
    return new Library().id(id).name("Library");
  }

  private static LoanType buildLoanType(String id) {
    return new LoanType().id(id).name("Loan type");
  }

  private static MaterialType buildMaterialType(String id) {
    return new MaterialType().id(id).name("Material type");
  }

  private static Item buildItem(ItemStatus.NameEnum status, String id, String holdingId, String locationId) {
    return new Item()
      .id(id)
      .holdingsRecordId(holdingId)
      .barcode("item_barcode")
      .status(new ItemStatus().name(status))
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
      .effectiveLocationId(locationId)
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
        .callNumber("CN")
        .prefix("PFX")
        .suffix("SFX"));
  }

  private static HoldingsRecord buildHolding(String id, String instanceId, String locationId) {
    return new HoldingsRecord()
      .id(id)
      .instanceId(instanceId)
      .permanentLocationId(locationId)
      .effectiveLocationId(locationId);
  }

  private static Instance buildInstance(String instanceId) {
    return new Instance()
      .id(instanceId)
      .title("Test title")
      .contributors(List.of(
        new InstanceContributorsInner().name("First, Author").primary(true),
        new InstanceContributorsInner().name("Second, Author").primary(null)
      ));
  }

  private static Location buildLocation() {
    return new Location()
      .id(randomId())
      .name("Test location")
      .discoveryDisplayName("Location display name")
      .libraryId(randomId())
      .campusId(randomId())
      .institutionId(randomId())
      .primaryServicePoint(randomUUID());
  }

  private static String randomId() {
    return randomUUID().toString();
  }

}
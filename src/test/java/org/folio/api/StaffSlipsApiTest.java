package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.requestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.folio.domain.dto.ItemStatus.NameEnum.AWAITING_DELIVERY;
import static org.folio.domain.dto.ItemStatus.NameEnum.CHECKED_OUT;
import static org.folio.domain.dto.ItemStatus.NameEnum.IN_PROCESS;
import static org.folio.domain.dto.ItemStatus.NameEnum.IN_TRANSIT;
import static org.folio.domain.dto.ItemStatus.NameEnum.MISSING;
import static org.folio.domain.dto.ItemStatus.NameEnum.ON_ORDER;
import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.ItemStatus.NameEnum.RESTRICTED;
import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.AddressTypes;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Campuses;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Departments;
import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.HoldingsRecords;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.InstanceContributorsInner;
import org.folio.domain.dto.Instances;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Institutions;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Items;
import org.folio.domain.dto.Libraries;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.LoanTypes;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Locations;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.MaterialTypes;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.ServicePoints;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserGroups;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserPersonalAddressesInner;
import org.folio.domain.dto.Users;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MultiValuePattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import lombok.SneakyThrows;

class StaffSlipsApiTest extends BaseIT {

  private static final String SERVICE_POINT_ID = "e0c50666-6144-47b1-9e87-8c1bf30cda34";
  private static final String DEFAULT_LIMIT = "1000";
  private static final EnumSet<ItemStatus.NameEnum> PICK_SLIPS_ITEM_STATUSES = EnumSet.of(PAGED);
  private static final EnumSet<ItemStatus.NameEnum> SEARCH_SLIPS_ITEM_STATUSES = EnumSet.of(
    CHECKED_OUT, AWAITING_DELIVERY, IN_TRANSIT, MISSING, PAGED, ON_ORDER, IN_PROCESS, RESTRICTED);

  private static final String PICK_SLIPS_URL = "/tlr/staff-slips/pick-slips";
  private static final String SEARCH_SLIPS_URL = "/tlr/staff-slips/search-slips";
  private static final String LOCATIONS_URL = "/locations";
  private static final String ITEMS_URL = "/item-storage/items";
  private static final String HOLDINGS_URL = "/holdings-storage/holdings";
  private static final String INSTANCES_URL = "/instance-storage/instances";
  private static final String REQUESTS_URL = "/request-storage/requests";
  private static final String MATERIAL_TYPES_URL = "/material-types";
  private static final String LOAN_TYPES_URL = "/loan-types";
  private static final String LIBRARIES_URL = "/location-units/libraries";
  private static final String CAMPUSES_URL = "/location-units/campuses";
  private static final String INSTITUTIONS_URL = "/location-units/institutions";
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String USERS_URL = "/users";
  private static final String USER_GROUPS_URL = "/groups";
  private static final String DEPARTMENTS_URL = "/departments";
  private static final String ADDRESS_TYPES_URL = "/addresstypes";

  private static final String PICK_SLIPS_LOCATION_QUERY =
    "primaryServicePoint==\"" + SERVICE_POINT_ID + "\"";
  private static final String SEARCH_BY_ID_QUERY_PATTERN = "id==\\(.*\\)";
  private static final String REQUESTS_QUERY_PATTERN_TEMPLATE = "requestType==\\(\"%s\"\\) " +
    "and \\(status==\\(\"Open - Not yet filled\"\\)\\) and \\(itemId==\\(.*\\)\\)";
  private static final String PICK_SLIPS_REQUESTS_QUERY_PATTERN =
    String.format(REQUESTS_QUERY_PATTERN_TEMPLATE, "Page");
  private static final String SEARCH_SLIPS_REQUESTS_QUERY_PATTERN =
    String.format(REQUESTS_QUERY_PATTERN_TEMPLATE, "Page");
  private static final String REQUESTS_WITHOUT_ITEM_QUERY_PATTERN =
    "requestType==\"Hold\"\\ and \\(requestLevel==\"Title\"\\) and " +
      "\\(status==\\(\"Open - Not yet filled\"\\)\\) not \\(itemId=\"\"\\)";
  private static final String ITEMS_QUERY_PATTERN_TEMPLATE =
    "status.name==\\(%s\\) and \\(effectiveLocationId==\\(.*\\)\\)";
  private static final String PICK_SLIPS_ITEMS_QUERY_PATTERN =
    String.format(ITEMS_QUERY_PATTERN_TEMPLATE, joinForMatchAnyQuery(PICK_SLIPS_ITEM_STATUSES));
  private static final String SEARCH_SLIPS_ITEMS_QUERY_PATTERN =
    String.format(ITEMS_QUERY_PATTERN_TEMPLATE, joinForMatchAnyQuery(SEARCH_SLIPS_ITEM_STATUSES));

  private static final String INSTITUTION_ID = randomId();
  private static final String CAMPUS_ID = randomId();
  private static final String LIBRARY_ID = randomId();
  private static final String PRIMARY_SERVICE_POINT_ID = randomId();
  private static final String MATERIAL_TYPE_ID = randomId();
  private static final String LOAN_TYPE_ID = randomId();

  @Test
  @SneakyThrows
  void pickSlipsAreBuiltSuccessfully() {
    Location locationCollege = buildLocation("Location college");
    createStubForLocations(List.of(locationCollege), TENANT_ID_COLLEGE);
    createStubForLocations(emptyList(), TENANT_ID_UNIVERSITY);
    createStubForLocations(emptyList(), TENANT_ID_CONSORTIUM);

    Instance instance = buildInstance("Test title");
    createStubForInstances(List.of(instance));

    HoldingsRecord holdingCollege = buildHolding(instance.getId(), randomId());
    createStubForHoldings(List.of(holdingCollege), TENANT_ID_COLLEGE);

    Item itemCollege = buildItem("item_barcode_college", PAGED, locationCollege.getId(),
      holdingCollege.getId());
    createStubForItems(List.of(itemCollege), List.of(locationCollege), TENANT_ID_COLLEGE,
      PICK_SLIPS_ITEMS_QUERY_PATTERN);

    User requester = buildUser("user_barcode");
    createStubForUsers(List.of(requester));

    Request requestForCollegeItem = buildRequest(PAGE, itemCollege.getId(), holdingCollege.getId(),
      instance.getId(), requester.getId());
    createStubForRequests(List.of(itemCollege.getId()), List.of(requestForCollegeItem),
      PICK_SLIPS_REQUESTS_QUERY_PATTERN);

    MaterialType materialType = buildMaterialType();
    createStubForMaterialTypes(List.of(materialType), TENANT_ID_COLLEGE);

    LoanType loanType = buildLoanType();
    createStubForLoanTypes(List.of(loanType), TENANT_ID_COLLEGE);

    Library library = buildLibrary();
    createStubForLibraries(List.of(library), TENANT_ID_COLLEGE);

    Campus campus = buildCampus();
    createStubForCampuses(List.of(campus), TENANT_ID_COLLEGE);

    Institution institution = buildInstitution();
    createStubForInstitutions(List.of(institution), TENANT_ID_COLLEGE);

    ServicePoint primaryServicePoint = buildServicePoint(PRIMARY_SERVICE_POINT_ID,
      "Primary service point");
    ServicePoint pickupServicePoint = buildServicePoint(
      requestForCollegeItem.getPickupServicePointId(), "Pickup service point");
    createStubForServicePoints(List.of(primaryServicePoint), TENANT_ID_COLLEGE);
    createStubForServicePoints(List.of(pickupServicePoint), TENANT_ID_CONSORTIUM);

    UserGroup userGroup = buildUserGroup(requester.getPatronGroup(), "Test user group");
    createStubForUserGroups(List.of(userGroup));

    List<Department> departments = buildDepartments(requester);
    createStubForDepartments(departments);

    List<AddressType> addressTypes = buildAddressTypes(requester);
    createStubForAddressTypes(addressTypes);

    getPickSlips()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("pickSlips").value(hasSize(1))
      .jsonPath("totalRecords").value(is(1))
      .jsonPath("pickSlips[*].currentDateTime").exists()
      .jsonPath("pickSlips[*].item").exists()
      .jsonPath("pickSlips[*].request").exists()
      .jsonPath("pickSlips[*].requester").exists();

    verifyOutgoingGetRequests(LOCATIONS_URL, 1, 1, 1);
    verifyOutgoingGetRequests(SERVICE_POINTS_URL, 1, 1, 0);
    verifyOutgoingGetRequests(ITEMS_URL, 0, 1, 0);
    verifyOutgoingGetRequests(HOLDINGS_URL, 0, 1, 0);
    verifyOutgoingGetRequests(INSTANCES_URL, 1, 0, 0);
    verifyOutgoingGetRequests(REQUESTS_URL, 1, 0, 0);
    verifyOutgoingGetRequests(USER_GROUPS_URL, 1, 0, 0);
    verifyOutgoingGetRequests(DEPARTMENTS_URL, 1, 0, 0);
    verifyOutgoingGetRequests(ADDRESS_TYPES_URL, 1, 0, 0);
    verifyOutgoingGetRequests(MATERIAL_TYPES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(LOAN_TYPES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(LIBRARIES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(CAMPUSES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(INSTITUTIONS_URL, 0, 1, 0);

    RequestPatternBuilder usersRequestPattern = getRequestedFor(urlPathMatching(USERS_URL))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN)); // to ignore system user's internal calls
    verifyOutgoingRequests(usersRequestPattern, 1, 0, 0);
  }

  @Test
  @SneakyThrows
  void searchSlipsAreBuiltSuccessfully() {
    Location locationCollege = buildLocation("Location college");
    Location locationUniversity = buildLocation("Location university");
    createStubForLocations(List.of(locationCollege), TENANT_ID_COLLEGE);
    createStubForLocations(List.of(locationUniversity), TENANT_ID_UNIVERSITY);
    createStubForLocations(emptyList(), TENANT_ID_CONSORTIUM);

    Instance instanceWithItem = buildInstance("Instance with item");
    Instance instanceWithoutItem = buildInstance("Instance without item");
    createStubForInstances(List.of(instanceWithItem));
    createStubForInstances(List.of(instanceWithoutItem));

    HoldingsRecord holdingWithItem = buildHolding(instanceWithItem.getId(), randomId());
    HoldingsRecord holdingWithoutItem = buildHolding(instanceWithoutItem.getId(),
      locationUniversity.getId());
    createStubForHoldings(emptyList(), TENANT_ID_COLLEGE, List.of(instanceWithoutItem.getId()));
    createStubForHoldings(List.of(holdingWithoutItem), TENANT_ID_UNIVERSITY,
      List.of(instanceWithoutItem.getId()));
    createStubForHoldings(List.of(holdingWithItem), TENANT_ID_COLLEGE, List.of(holdingWithItem.getId()));

    Item itemCollege = buildItem("item_barcode_college", CHECKED_OUT, locationCollege.getId(),
      holdingWithItem.getId());
    createStubForItems(List.of(itemCollege), List.of(locationCollege), TENANT_ID_COLLEGE,
      SEARCH_SLIPS_ITEMS_QUERY_PATTERN);
    createStubForItems(emptyList(), List.of(locationUniversity), TENANT_ID_UNIVERSITY,
      SEARCH_SLIPS_ITEMS_QUERY_PATTERN);

    User requester = buildUser("user_barcode");
    createStubForUsers(List.of(requester));

    Request requestWithItem = buildRequest(HOLD, itemCollege.getId(), holdingWithItem.getId(),
      instanceWithItem.getId(), requester.getId());
    Request requestWithoutItemId = buildRequest(HOLD, null, null, instanceWithoutItem.getId(),
      requester.getId());
    createStubForRequests(List.of(itemCollege.getId()), List.of(requestWithItem),
      SEARCH_SLIPS_REQUESTS_QUERY_PATTERN);
    createStubForRequests(List.of(requestWithoutItemId), REQUESTS_WITHOUT_ITEM_QUERY_PATTERN);

    MaterialType materialType = buildMaterialType();
    createStubForMaterialTypes(List.of(materialType), TENANT_ID_COLLEGE);

    LoanType loanType = buildLoanType();
    createStubForLoanTypes(List.of(loanType), TENANT_ID_COLLEGE);

    Library library = buildLibrary();
    createStubForLibraries(List.of(library), TENANT_ID_COLLEGE);

    Campus campus = buildCampus();
    createStubForCampuses(List.of(campus), TENANT_ID_COLLEGE);

    Institution institution = buildInstitution();
    createStubForInstitutions(List.of(institution), TENANT_ID_COLLEGE);

    ServicePoint primaryServicePoint = buildServicePoint(PRIMARY_SERVICE_POINT_ID,
      "Primary service point");
    ServicePoint pickupServicePoint = buildServicePoint(
      requestWithItem.getPickupServicePointId(), "Pickup service point");
    createStubForServicePoints(List.of(primaryServicePoint), TENANT_ID_COLLEGE);
    createStubForServicePoints(List.of(pickupServicePoint), TENANT_ID_CONSORTIUM);

    UserGroup userGroup = buildUserGroup(requester.getPatronGroup(), "Test user group");
    createStubForUserGroups(List.of(userGroup));

    List<Department> departments = buildDepartments(requester);
    createStubForDepartments(departments);

    List<AddressType> addressTypes = buildAddressTypes(requester);
    createStubForAddressTypes(addressTypes);

    getSearchSlips()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("searchSlips").value(hasSize(2))
      .jsonPath("totalRecords").value(is(2))
      .jsonPath("searchSlips[*].currentDateTime").exists()
      .jsonPath("searchSlips[*].item").exists()
      .jsonPath("searchSlips[*].request").exists()
      .jsonPath("searchSlips[*].requester").exists();

    verifyOutgoingGetRequests(LOCATIONS_URL, 1, 1, 1);
    verifyOutgoingGetRequests(SERVICE_POINTS_URL, 1, 1, 0);
    verifyOutgoingGetRequests(ITEMS_URL, 0, 1, 1);
    verifyOutgoingGetRequests(HOLDINGS_URL, 0, 2, 1);
    verifyOutgoingGetRequests(INSTANCES_URL, 2, 0, 0);
    verifyOutgoingGetRequests(REQUESTS_URL, 2, 0, 0);
    verifyOutgoingGetRequests(USER_GROUPS_URL, 1, 0, 0);
    verifyOutgoingGetRequests(DEPARTMENTS_URL, 1, 0, 0);
    verifyOutgoingGetRequests(ADDRESS_TYPES_URL, 1, 0, 0);
    verifyOutgoingGetRequests(MATERIAL_TYPES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(LOAN_TYPES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(LIBRARIES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(CAMPUSES_URL, 0, 1, 0);
    verifyOutgoingGetRequests(INSTITUTIONS_URL, 0, 1, 0);

    RequestPatternBuilder usersRequestPattern = getRequestedFor(urlPathMatching(USERS_URL))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN)); // to ignore system user's internal calls
    verifyOutgoingRequests(usersRequestPattern, 1, 0, 0);
  }

  private WebTestClient.ResponseSpec getPickSlips() {
    return getPickSlips(SERVICE_POINT_ID);
  }

  private WebTestClient.ResponseSpec getSearchSlips() {
    return getSearchSlips(SERVICE_POINT_ID);
  }

  @SneakyThrows
  private WebTestClient.ResponseSpec getPickSlips(String servicePointId) {
    return doGet(PICK_SLIPS_URL + "/" + servicePointId);
  }

  @SneakyThrows
  private WebTestClient.ResponseSpec getSearchSlips(String servicePointId) {
    return doGet(SEARCH_SLIPS_URL + "/" + servicePointId);
  }

  private static Location buildLocation(String name) {
    return new Location()
      .id(randomId())
      .name(name)
      .discoveryDisplayName(name + " discovery display name")
      .libraryId(LIBRARY_ID)
      .campusId(CAMPUS_ID)
      .institutionId(INSTITUTION_ID)
      .primaryServicePoint(UUID.fromString(PRIMARY_SERVICE_POINT_ID));
  }

  private static Instance buildInstance(String title) {
    return new Instance()
      .id(randomId())
      .title(title)
      .contributors(List.of(
        new InstanceContributorsInner().name("First, Author").primary(true),
        new InstanceContributorsInner().name("Second, Author").primary(null)));
  }

  private static Item buildItem(String barcode, ItemStatus.NameEnum status, String locationId,
    String holdingId) {

    return new Item()
      .id(randomId())
      .barcode(barcode)
      .holdingsRecordId(holdingId)
      .status(new ItemStatus(status))
      .effectiveLocationId(locationId)
      .materialTypeId(MATERIAL_TYPE_ID)
      .permanentLoanTypeId(LOAN_TYPE_ID);
  }

  private static HoldingsRecord buildHolding(String instanceId, String locationId) {
    return new HoldingsRecord()
      .id(randomId())
      .instanceId(instanceId)
      .copyNumber("Holding copy number")
      .permanentLocationId(locationId)
      .effectiveLocationId(locationId);
  }

  private static HoldingsRecord buildHolding(String locationId) {
    return new HoldingsRecord()
      .id(randomId())
      .copyNumber("Holding copy number")
      .effectiveLocationId(locationId);
  }

  private static Request buildRequest(Request.RequestTypeEnum requestTypeEnum,  String itemId,
    String holdingId, String instanceId, String requesterId) {

    return new Request()
      .id(randomId())
      .requestType(requestTypeEnum)
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .itemId(itemId)
      .holdingsRecordId(holdingId)
      .instanceId(instanceId)
      .pickupServicePointId(randomId())
      .requesterId(requesterId);
  }

  private static MaterialType buildMaterialType() {
    return new MaterialType()
      .id(MATERIAL_TYPE_ID)
      .name("Test material type");
  }

  private static LoanType buildLoanType() {
    return new LoanType()
      .id(LOAN_TYPE_ID)
      .name("Test loan type");
  }

  private static Library buildLibrary() {
    return new Library()
      .id(LIBRARY_ID)
      .name("Test library")
      .campusId(CAMPUS_ID);
  }

  private static Campus buildCampus() {
    return new Campus()
      .id(CAMPUS_ID)
      .name("Test campus")
      .institutionId(INSTITUTION_ID);
  }

  private static Institution buildInstitution() {
    return new Institution()
      .id(INSTITUTION_ID)
      .name("Test institution");
  }

  private static ServicePoint buildServicePoint(String id, String name) {
    return new ServicePoint()
      .id(id)
      .name(name);
  }

  private static User buildUser(String barcode) {
    return new User()
      .id(randomId())
      .barcode(barcode)
      .departments(Set.of(randomId(), randomId()))
      .patronGroup(randomId())
      .personal(new UserPersonal()
        .firstName("First name")
        .middleName("Middle name")
        .lastName("Last name")
        .preferredFirstName("Preferred first name")
        .addresses(List.of(
          new UserPersonalAddressesInner()
            .id(randomId())
            .addressTypeId(randomId())
            .primaryAddress(true),
          new UserPersonalAddressesInner()
            .id(randomId())
            .addressTypeId(randomId())
            .primaryAddress(false)
        )));
  }

  private static UserGroup buildUserGroup(String id, String name) {
    return new UserGroup()
      .id(id)
      .group(name);
  }

  private static List<Department> buildDepartments(User requester) {
    return requester.getDepartments()
      .stream()
      .map(id -> buildDepartment(id, "Department " + id))
      .toList();
  }

  private static Department buildDepartment(String id, String name) {
    return new Department()
      .id(id)
      .name(name);
  }

  private static List<AddressType> buildAddressTypes(User requester) {
    return requester.getPersonal()
      .getAddresses()
      .stream()
      .map(UserPersonalAddressesInner::getAddressTypeId)
      .map(id -> buildAddressType(id, "Address type " + id))
      .toList();
  }

  private static AddressType buildAddressType(String id, String name) {
    return new AddressType()
      .id(id)
      .addressType(name);
  }

  private static void createStubForLocations(List<Location> locations, String tenantId) {
    Locations mockResponse = new Locations()
      .locations(locations)
      .totalRecords(locations.size());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withHeader(HEADER_TENANT, equalTo(tenantId))
      .willReturn(okJson(asJsonString(mockResponse))));
  }



  private static void createStubForRequests(Collection<String> itemIds,
    List<Request> requests, String queryPattern) {

    Requests mockResponse = new Requests()
      .requests(requests)
      .totalRecords(requests.size());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUESTS_URL))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withQueryParam("query", matching(queryPattern))
      .withQueryParam("query", containsInAnyOrder(itemIds))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static void createStubForRequests(List<Request> requests, String queryPattern) {
    Requests mockResponse = new Requests()
      .requests(requests)
      .totalRecords(requests.size());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUESTS_URL))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withQueryParam("query", matching(queryPattern))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static void createStubForItems(List<Item> items, Collection<Location> locations,
    String tenantId, String queryPattern) {

    Items mockResponse = new Items()
      .items(items)
      .totalRecords(items.size());

    Set<String> locationIds = locations.stream()
      .map(Location::getId)
      .collect(toSet());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(ITEMS_URL))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withQueryParam("query", matching(queryPattern))
      .withQueryParam("query", containsInAnyOrder(locationIds))
      .withHeader(HEADER_TENANT, equalTo(tenantId))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static void createStubForHoldings(List<HoldingsRecord> holdings, String tenantId) {
    HoldingsRecords mockResponse = new HoldingsRecords()
      .holdingsRecords(holdings)
      .totalRecords(holdings.size());

    Set<String> ids = holdings.stream()
      .map(HoldingsRecord::getId)
      .collect(toSet());

    createStubForGetByIds(HOLDINGS_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForHoldings(List<HoldingsRecord> holdings, String tenantId,
    Collection<String> ids) {

    HoldingsRecords mockResponse = new HoldingsRecords()
      .holdingsRecords(holdings)
      .totalRecords(holdings.size());

    createStubForGetByIds(HOLDINGS_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForInstances(List<Instance> instances) {
    Instances mockResponse = new Instances()
      .instances(instances)
      .totalRecords(instances.size());

    Set<String> ids = instances.stream()
      .map(Instance::getId)
      .collect(toSet());

    createStubForGetByIds(INSTANCES_URL, ids, mockResponse, TENANT_ID_CONSORTIUM);
  }

  private static void createStubForMaterialTypes(List<MaterialType> materialTypes, String tenantId) {
    MaterialTypes mockResponse = new MaterialTypes()
      .mtypes(materialTypes)
      .totalRecords(materialTypes.size());

    Set<String> ids = materialTypes.stream()
      .map(MaterialType::getId)
      .collect(toSet());

    createStubForGetByIds(MATERIAL_TYPES_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForLoanTypes(List<LoanType> loanTypes, String tenantId) {
    LoanTypes mockResponse = new LoanTypes()
      .loantypes(loanTypes)
      .totalRecords(loanTypes.size());

    Set<String> ids = loanTypes.stream()
      .map(LoanType::getId)
      .collect(toSet());

    createStubForGetByIds(LOAN_TYPES_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForLibraries(List<Library> libraries, String tenantId) {
    Libraries mockResponse = new Libraries()
      .loclibs(libraries)
      .totalRecords(libraries.size());

    Set<String> ids = libraries.stream()
      .map(Library::getId)
      .collect(toSet());

    createStubForGetByIds(LIBRARIES_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForCampuses(List<Campus> campuses, String tenantId) {
    Campuses mockResponse = new Campuses()
      .loccamps(campuses)
      .totalRecords(campuses.size());

    Set<String> ids = campuses.stream()
      .map(Campus::getId)
      .collect(toSet());

    createStubForGetByIds(CAMPUSES_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForInstitutions(List<Institution> institutions, String tenantId) {
    Institutions mockResponse = new Institutions()
      .locinsts(institutions)
      .totalRecords(institutions.size());

    Set<String> ids = institutions.stream()
      .map(Institution::getId)
      .collect(toSet());

    createStubForGetByIds(INSTITUTIONS_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForServicePoints(List<ServicePoint> servicePoints, String tenantId) {
    ServicePoints mockResponse = new ServicePoints()
      .servicepoints(servicePoints)
      .totalRecords(servicePoints.size());

    Set<String> ids = servicePoints.stream()
      .map(ServicePoint::getId)
      .collect(toSet());

    createStubForGetByIds(SERVICE_POINTS_URL, ids, mockResponse, tenantId);
  }

  private static void createStubForUsers(List<User> users) {
    Users mockResponse = new Users()
      .users(users)
      .totalRecords(users.size());

    Set<String> ids = users.stream()
      .map(User::getId)
      .collect(toSet());

    createStubForGetByIds(USERS_URL, ids, mockResponse, TENANT_ID_CONSORTIUM);
  }

  private static void createStubForUserGroups(List<UserGroup> userGroups) {
    UserGroups mockResponse = new UserGroups()
      .usergroups(userGroups)
      .totalRecords(userGroups.size());

    Set<String> ids = userGroups.stream()
      .map(UserGroup::getId)
      .collect(toSet());

    createStubForGetByIds(USER_GROUPS_URL, ids, mockResponse, TENANT_ID_CONSORTIUM);
  }

  private static void createStubForDepartments(List<Department> departments) {
    Departments mockResponse = new Departments()
      .departments(departments)
      .totalRecords(departments.size());

    Set<String> ids = departments.stream()
      .map(Department::getId)
      .collect(toSet());

    createStubForGetByIds(DEPARTMENTS_URL, ids, mockResponse, TENANT_ID_CONSORTIUM);
  }

  private static void createStubForAddressTypes(List<AddressType> addressTypes) {
    AddressTypes mockResponse = new AddressTypes()
      .addressTypes(addressTypes)
      .totalRecords(addressTypes.size());

    Set<String> ids = addressTypes.stream()
      .map(AddressType::getId)
      .collect(toSet());

    createStubForGetByIds(ADDRESS_TYPES_URL, ids, mockResponse, TENANT_ID_CONSORTIUM);
  }

  private static <T> void createStubForGetByIds(String url, Collection<String> ids,
    T mockResponse, String tenantId) {

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(url))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN))
      .withQueryParam("query", containsInAnyOrder(ids))
      .withHeader(HEADER_TENANT, equalTo(tenantId))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static MultiValuePattern containsInAnyOrder(Collection<String> values) {
    return WireMock.including(values.stream()
      .map(WireMock::containing)
      .toArray(StringValuePattern[]::new));
  }

  private static String joinForMatchAnyQuery(EnumSet<ItemStatus.NameEnum> itemStatuses) {
    return joinForMatchAnyQuery(
      itemStatuses.stream()
        .map(ItemStatus.NameEnum::getValue)
        .collect(toSet())
    );
  }

  private static String joinForMatchAnyQuery(Collection<String> values) {
    return values.stream()
      .map(value -> "\"" + value + "\"")
      .collect(joining(" or "));
  }

  private static void verifyOutgoingGetRequests(String urlPattern, int requestsToConsortium,
    int requestsToCollege, int requestsToUniversity) {

    verifyOutgoingRequests(HttpMethod.GET, urlPattern, requestsToConsortium,
      requestsToCollege, requestsToUniversity);
  }

  private static void verifyOutgoingRequests(HttpMethod method, String urlPattern,
    int requestsToConsortium, int requestsToCollege, int requestsToUniversity) {

    RequestPatternBuilder requestPattern = requestedFor(method.name(), urlPathMatching(urlPattern));
    verifyOutgoingRequests(requestPattern, requestsToConsortium, requestsToCollege, requestsToUniversity);
  }

  private static void verifyOutgoingRequests(RequestPatternBuilder requestPatternBuilder,
    int requestsToConsortium, int requestsToCollege, int requestsToUniversity) {

    wireMockServer.verify(requestsToConsortium, requestPatternBuilder.withHeader(HEADER_TENANT,
      equalTo(TENANT_ID_CONSORTIUM)));
    wireMockServer.verify(requestsToCollege, requestPatternBuilder.withHeader(HEADER_TENANT,
      equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(requestsToUniversity, requestPatternBuilder.withHeader(HEADER_TENANT,
      equalTo(TENANT_ID_UNIVERSITY)));
  }

}

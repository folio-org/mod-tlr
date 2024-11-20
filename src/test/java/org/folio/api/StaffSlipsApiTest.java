package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.domain.dto.AddressType;
import org.folio.domain.dto.AddressTypes;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Campuses;
import org.folio.domain.dto.Contributor;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Departments;
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
import org.folio.domain.dto.SearchHolding;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.dto.SearchItemStatus;
import org.folio.domain.dto.ServicePoint;
import org.folio.domain.dto.ServicePoints;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserGroups;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserPersonalAddressesInner;
import org.folio.domain.dto.Users;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MultiValuePattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import lombok.SneakyThrows;

class StaffSlipsApiTest extends BaseIT {

  private static final String SERVICE_POINT_ID = "e0c50666-6144-47b1-9e87-8c1bf30cda34";
  private static final String DEFAULT_LIMIT = "1000";

  private static final String PICK_SLIPS_URL = "/tlr/staff-slips/pick-slips";
  private static final String INSTANCE_SEARCH_URL ="/search/instances";
  private static final String LOCATIONS_URL = "/locations";
  private static final String ITEMS_URL = "/item-storage/items";
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
  private static final String PICK_SLIPS_REQUESTS_QUERY_PATTERN = "requestType==\\(\"Page\"\\) " +
    "and \\(status==\\(\"Open - Not yet filled\"\\)\\) and \\(itemId==\\(.*\\)\\)";
  private static final String PICK_SLIPS_INSTANCE_SEARCH_QUERY_PATTERN =
    "item.status.name==\\(\"Paged\"\\) and \\(item.effectiveLocationId==\\(.*\\)\\)";

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

    SearchItem searchItemCollege = buildSearchItem("item_barcode_college", PAGED,
      locationCollege.getId(), TENANT_ID_COLLEGE);
    SearchHolding searchHoldingCollege = buildSearchHolding(searchItemCollege);
    SearchInstance searchInstanceCollege = buildSearchInstance("title_college",
      List.of(searchHoldingCollege), List.of(searchItemCollege));
    createStubForInstanceSearch(List.of(locationCollege.getId()), List.of(searchInstanceCollege));

    Request requestForCollegeItem = buildRequest(PAGE, searchItemCollege, randomId());
    createStubForRequests(List.of(searchItemCollege.getId()), List.of(requestForCollegeItem));

    Item itemCollege = buildItem(searchItemCollege);
    createStubForItems(List.of(itemCollege), TENANT_ID_COLLEGE);

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

    User requester = buildUser(requestForCollegeItem.getRequesterId(), "user_barcode");
    createStubForUsers(List.of(requester));

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

    // verify that locations were searched in all tenants
    Stream.of(TENANT_ID_CONSORTIUM, TENANT_ID_COLLEGE, TENANT_ID_UNIVERSITY)
      .forEach(tenantId -> wireMockServer.verify(getRequestedFor(urlPathMatching(LOCATIONS_URL))
        .withHeader(HEADER_TENANT, equalTo(tenantId))));

    // verify that service points were searched only in central tenant (pickup service point)
    // and lending tenant (item's location primary service point)
    wireMockServer.verify(getRequestedFor(urlPathMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));
    wireMockServer.verify(getRequestedFor(urlPathMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(0, getRequestedFor(urlPathMatching(SERVICE_POINTS_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));

    // verify that requesters were searched in central tenant only
    wireMockServer.verify(getRequestedFor(urlPathMatching(USERS_URL))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN)) // to ignore system user's internal calls
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));
    wireMockServer.verify(0, getRequestedFor(urlPathMatching(USERS_URL))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));
    wireMockServer.verify(0, getRequestedFor(urlPathMatching(USERS_URL))
      .withQueryParam("query", matching(SEARCH_BY_ID_QUERY_PATTERN))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));

    // verify interactions with central tenant only
    Stream.of(INSTANCE_SEARCH_URL, REQUESTS_URL, USER_GROUPS_URL, DEPARTMENTS_URL, ADDRESS_TYPES_URL)
      .forEach(url -> {
        wireMockServer.verify(getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));
        wireMockServer.verify(0, getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));
        wireMockServer.verify(0, getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));
      });

    // verify interactions with lending tenant only
    Stream.of(ITEMS_URL, MATERIAL_TYPES_URL, LOAN_TYPES_URL, LIBRARIES_URL, CAMPUSES_URL, INSTITUTIONS_URL)
      .forEach(url -> {
        wireMockServer.verify(0, getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM)));
        wireMockServer.verify(getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE)));
        wireMockServer.verify(0, getRequestedFor(urlPathMatching(url))
          .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));
      });
  }

  private WebTestClient.ResponseSpec getPickSlips() {
    return getPickSlips(SERVICE_POINT_ID);
  }

  @SneakyThrows
  private WebTestClient.ResponseSpec getPickSlips(String servicePointId) {
    return doGet(PICK_SLIPS_URL + "/" + servicePointId);
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

  private static SearchItem buildSearchItem(String barcode, ItemStatus.NameEnum itemStatus,
    String locationId, String tenant) {

    return new SearchItem()
      .id(randomId())
      .tenantId(tenant)
      .barcode(barcode)
      .holdingsRecordId(randomId())
      .status(new SearchItemStatus().name(itemStatus.getValue()))
      .effectiveLocationId(locationId)
      .materialTypeId(MATERIAL_TYPE_ID);
  }

  private static SearchInstance buildSearchInstance(String title, List<SearchHolding> holdings,
    List<SearchItem> items) {

    return new SearchInstance()
      .id(randomId())
      .tenantId(TENANT_ID_CONSORTIUM)
      .title(title)
      .holdings(holdings)
      .items(items)
      .contributors(List.of(
        new Contributor().name("First, Author").primary(true),
        new Contributor().name("Second, Author")));
  }

  private static SearchHolding buildSearchHolding(SearchItem searchItem) {
    return new SearchHolding()
      .id(searchItem.getHoldingsRecordId())
      .tenantId(searchItem.getTenantId());
  }

  private static Item buildItem(SearchItem searchItem) {
    return new Item()
      .id(searchItem.getId())
      .barcode(searchItem.getBarcode())
      .holdingsRecordId(searchItem.getHoldingsRecordId())
      .status(new ItemStatus(ItemStatus.NameEnum.fromValue(searchItem.getStatus().getName())))
      .effectiveLocationId(searchItem.getEffectiveLocationId())
      .materialTypeId(searchItem.getMaterialTypeId())
      .permanentLoanTypeId(LOAN_TYPE_ID);
  }

  private static Request buildRequest(Request.RequestTypeEnum requestTypeEnum,  SearchItem item,
    String requesterId) {

    return new Request()
      .id(randomId())
      .requestType(requestTypeEnum)
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .itemId(item.getId())
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

  private static User buildUser(String id, String barcode) {
    return new User()
      .id(id)
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

  private static void createStubForInstanceSearch(Collection<String> locationIds,
    List<SearchInstance> instances) {

    SearchInstancesResponse mockResponse = new SearchInstancesResponse()
      .instances(instances)
      .totalRecords(instances.size());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(INSTANCE_SEARCH_URL))
      .withQueryParam("expandAll", equalTo("true"))
      .withQueryParam("limit", equalTo("500"))
      .withQueryParam("query", matching(PICK_SLIPS_INSTANCE_SEARCH_QUERY_PATTERN))
      .withQueryParam("query", containsInAnyOrder(locationIds))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static void createStubForRequests(Collection<String> itemIds,
    List<Request> requests) {

    Requests mockResponse = new Requests()
      .requests(requests)
      .totalRecords(requests.size());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUESTS_URL))
      .withQueryParam("limit", equalTo(DEFAULT_LIMIT))
      .withQueryParam("query", matching(PICK_SLIPS_REQUESTS_QUERY_PATTERN))
      .withQueryParam("query", containsInAnyOrder(itemIds))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockResponse))));
  }

  private static void createStubForItems(List<Item> items, String tenantId) {
    Items mockResponse = new Items()
      .items(items)
      .totalRecords(items.size());

    Set<String> ids = items.stream()
      .map(Item::getId)
      .collect(toSet());

    createStubForGetByIds(ITEMS_URL, ids, mockResponse, tenantId);
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
}

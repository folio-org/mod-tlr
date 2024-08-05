package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;

import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.AllowedServicePointsInner;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllowedServicePointsApiTest extends BaseIT {
  private static final String INSTANCE_ID = randomId();
  private static final String REQUESTER_ID = randomId();
  private static final String PATRON_GROUP_ID = randomId();
  private static final String ALLOWED_SERVICE_POINTS_URL = "/tlr/allowed-service-points";
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL =
    "/circulation/requests/allowed-service-points.*";
  private static final String SEARCH_INSTANCES_URL = "/search/instances.*";
  private static final String USER_URL = "/users/" + REQUESTER_ID;

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @Test
  void allowedServicePointReturnsEmptyResultWhenNoRoutingSpInResponsesFromDataTenants() {
    var item1 = new Item();
    item1.setTenantId(TENANT_ID_UNIVERSITY);

    var item2 = new Item();
    item2.setTenantId(TENANT_ID_COLLEGE);

    var searchInstancesResponse = new SearchInstancesResponse();
    searchInstancesResponse.setTotalRecords(1);
    searchInstancesResponse.setInstances(List.of(new Instance().items(List.of(item1, item2))));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(searchInstancesResponse), HttpStatus.SC_OK)));

    var allowedSpResponseConsortium = new AllowedServicePointsResponse();
    allowedSpResponseConsortium.setHold(Set.of(
      buildAllowedServicePoint("SP_consortium_1"),
      buildAllowedServicePoint("SP_consortium_2")));
    allowedSpResponseConsortium.setPage(null);
    allowedSpResponseConsortium.setRecall(Set.of(
      buildAllowedServicePoint("SP_consortium_3")));

    var allowedSpResponseUniversity = new AllowedServicePointsResponse();
    allowedSpResponseUniversity.setHold(null);
    allowedSpResponseUniversity.setPage(null);
    allowedSpResponseUniversity.setRecall(null);

    var allowedSpResponseCollege = new AllowedServicePointsResponse();
    allowedSpResponseCollege.setHold(null);
    allowedSpResponseCollege.setPage(null);
    allowedSpResponseCollege.setRecall(null);

    var allowedSpResponseCollegeWithRouting = new AllowedServicePointsResponse();
    allowedSpResponseCollegeWithRouting.setHold(null);
    allowedSpResponseCollegeWithRouting.setPage(Set.of(
      buildAllowedServicePoint("SP_college_1")));
    allowedSpResponseCollegeWithRouting.setRecall(null);

    User requester = new User().patronGroup(PATRON_GROUP_ID);
    wireMockServer.stubFor(get(urlMatching(USER_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(requester), HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseConsortium), HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseUniversity), HttpStatus.SC_OK)));

    var collegeStubMapping = wireMockServer.stubFor(
      get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .willReturn(jsonResponse(asJsonString(allowedSpResponseCollege), HttpStatus.SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        REQUESTER_ID, INSTANCE_ID))
      .expectStatus().isEqualTo(200)
      .expectBody().json("{}");

    wireMockServer.removeStub(collegeStubMapping);
    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseCollegeWithRouting),
        HttpStatus.SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        REQUESTER_ID, INSTANCE_ID))
      .expectStatus().isEqualTo(200)
      .expectBody().json(asJsonString(allowedSpResponseConsortium));

    wireMockServer.verify(getRequestedFor(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withQueryParam("patronGroupId", equalTo(PATRON_GROUP_ID))
      .withQueryParam("instanceId", equalTo(INSTANCE_ID))
      .withQueryParam("operation", equalTo("create"))
      .withQueryParam("useStubItem", equalTo("true")));
  }

  @Test
  void allowedServicePointsShouldReturn422WhenParametersAreInvalid() {
    doGet(ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s", randomId()))
      .expectStatus().isEqualTo(422);
  }

  private AllowedServicePointsInner buildAllowedServicePoint(String name) {
    return new AllowedServicePointsInner()
      .id(randomId())
      .name(name);
  }
}

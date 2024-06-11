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
import org.folio.domain.dto.Holding;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.SearchInstancesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllowedServicePointsApiTest extends BaseIT {
  private static final String ALLOWED_SERVICE_POINTS_URL = "/tlr/allowed-service-points";
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL =
    "/circulation/requests/allowed-service-points(.*)";
  private static final String SEARCH_INSTANCES_URL =
    "/search/instances(.*)";
  private static final String TENANT_HEADER = "x-okapi-tenant";

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @Test
  void allowedServicePointReturnsEmptyResultWhenNoRoutingSpInResponsesFromDataTenants() {
    var item1 = new Item(List.of());
    item1.setTenantId(TENANT_ID_UNIVERSITY);

    var item2 = new Item(List.of());
    item2.setTenantId(TENANT_ID_COLLEGE);

    var searchInstancesResponse = new SearchInstancesResponse();
    searchInstancesResponse.setTotalRecords(1);
    searchInstancesResponse.setInstances(List.of(new Instance(List.of(), List.of(),
      List.of(item1, item2),
      List.of(new Holding(List.of(), List.of()))
    )));

    wireMockServer.stubFor(get(urlMatching(SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(searchInstancesResponse), HttpStatus.SC_OK)));

    var allowedSpResponseConsortium = new AllowedServicePointsResponse();
    allowedSpResponseConsortium.setHold(Set.of(
      buildAllowedServicePoint("SP_consortium_1", null),
      buildAllowedServicePoint("SP_consortium_2", null)));
    allowedSpResponseConsortium.setPage(null);
    allowedSpResponseConsortium.setRecall(Set.of(
      buildAllowedServicePoint("SP_consortium_3", null)));

    var allowedSpResponseUniversity = new AllowedServicePointsResponse();
    allowedSpResponseUniversity.setHold(Set.of(
      buildAllowedServicePoint("SP_university_1", false),
      buildAllowedServicePoint("SP_university_2", false)));
    allowedSpResponseUniversity.setPage(null);
    allowedSpResponseUniversity.setRecall(null);

    var allowedSpResponseCollege = new AllowedServicePointsResponse();
    allowedSpResponseCollege.setHold(null);
    allowedSpResponseCollege.setPage(Set.of(
      buildAllowedServicePoint("SP_college_1", false)));
    allowedSpResponseCollege.setRecall(null);

    var allowedSpResponseCollegeWithRouting = new AllowedServicePointsResponse();
    allowedSpResponseCollegeWithRouting.setHold(null);
    allowedSpResponseCollegeWithRouting.setPage(Set.of(
      buildAllowedServicePoint("SP_college_1", true)));
    allowedSpResponseCollegeWithRouting.setRecall(null);

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseConsortium), HttpStatus.SC_OK)));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseUniversity), HttpStatus.SC_OK)));

    var collegeStubMapping = wireMockServer.stubFor(
      get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
        .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
        .willReturn(jsonResponse(asJsonString(allowedSpResponseCollege), HttpStatus.SC_OK)));

    String requesterId = randomId();
    String instanceId = randomId();
    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        requesterId, instanceId))
      .expectStatus().isEqualTo(200)
      .expectBody().json("{}");

    wireMockServer.removeStub(collegeStubMapping);
    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE))
      .willReturn(jsonResponse(asJsonString(allowedSpResponseCollegeWithRouting),
        HttpStatus.SC_OK)));

    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?operation=create&requesterId=%s&instanceId=%s",
        requesterId, instanceId))
      .expectStatus().isEqualTo(200)
      .expectBody().json(asJsonString(allowedSpResponseConsortium));

    wireMockServer.verify(getRequestedFor(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withQueryParam("requesterId", equalTo(requesterId))
      .withQueryParam("instanceId", equalTo(instanceId))
      .withQueryParam("operation", equalTo("create"))
      .withQueryParam("useStubItem", equalTo("true")));
  }

  private AllowedServicePointsInner buildAllowedServicePoint(String name,
    Boolean ecsRequestRouting) {

    return new AllowedServicePointsInner()
      .id(randomId())
      .name(name)
      .ecsRequestRouting(ecsRequestRouting);
  }
}

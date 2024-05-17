package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;

import java.util.List;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.AllowedServicePointsInner;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AllowedServicePointsApiTest extends BaseIT {
  private static final String ALLOWED_SERVICE_POINTS_URL = "/tlr/allowed-service-points";
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL =
    "/circulation/requests/allowed-service-points(.*)";

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @Test
  void allowedServicePointCallProxiedToModCirculationEndpoint() {
    AllowedServicePointsResponse modCirculationMockedResponse = new AllowedServicePointsResponse();
    modCirculationMockedResponse.setHold(List.of(
      new AllowedServicePointsInner().id(randomId()).name("SP1"),
      new AllowedServicePointsInner().id(randomId()).name("SP2")));
    modCirculationMockedResponse.setPage(null);
    modCirculationMockedResponse.setRecall(List.of(
      new AllowedServicePointsInner().id(randomId()).name("SP3")));

    System.out.println(asJsonString(modCirculationMockedResponse));

    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .willReturn(jsonResponse(asJsonString(modCirculationMockedResponse), HttpStatus.SC_OK)));

    String requesterId = randomId();
    String instanceId = randomId();
    doGet(
      ALLOWED_SERVICE_POINTS_URL + format("?requesterId=%s&instanceId=%s", requesterId, instanceId))
      .expectStatus().isEqualTo(200)
      .expectBody().json(asJsonString(modCirculationMockedResponse));

    wireMockServer.verify(getRequestedFor(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
      .withQueryParam("requesterId", equalTo(requesterId))
      .withQueryParam("instanceId", equalTo(instanceId))
      .withQueryParam("useStubItem", equalTo("true")));
  }
}

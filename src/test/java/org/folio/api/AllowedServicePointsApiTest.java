package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.AllowedServicePointsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AllowedServicePointsApiTest extends BaseIT {
  private static final String ALLOWED_SERVICE_POINTS_URL = "/tlr/ecs-tlr";
  private static final String ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL =
    "/requests/allowed-service-points";

  @BeforeEach
  public void beforeEach() {
    wireMockServer.resetAll();
  }

  @Test
  void allowedServicePointCallProxiedToModCirculationEndpoint() {
    AllowedServicePointsResponse modCirculationMockedResponse = new AllowedServicePointsResponse();
//    modCirculationMockedResponse.setHold();
//    wireMockServer.stubFor(get(urlMatching(ALLOWED_SERVICE_POINTS_MOD_CIRCULATION_URL))
//      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));
  }
}

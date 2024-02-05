package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;

import com.github.tomakehurst.wiremock.client.WireMock;

class EcsTlrApiTest extends BaseIT {
  private static final String TLR_URL = "/tlr/ecs-tlr";
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String INSTANCE_ID = randomId();
  private static final String INSTANCE_REQUESTS_URL = "/circulation/requests/instances";
  private static final String SEARCH_INSTANCES_URL =
    "/search/instances\\?query=id==" + INSTANCE_ID + "&expandAll=true";

  @Autowired
  private FolioExecutionContext context;
  @Autowired
  private FolioModuleMetadata moduleMetadata;
  @Autowired
  private TestRestTemplate restTemplate;
  private FolioExecutionContextSetter contextSetter;

  @BeforeEach
  public void beforeEach() {
    contextSetter = initContext();
  }

  @AfterEach
  public void afterEach() {
    contextSetter.close();
  }

  @Test
  void getByIdNotFound() throws Exception {
    mockMvc.perform(
      get(TLR_URL + "/" + UUID.randomUUID())
        .headers(defaultHeaders())
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isNotFound());
  }

  @Test
  void titleLevelRequestIsCreatedForDifferentTenant() {
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID);
    String ecsTlrJson = asJsonString(ecsTlr);

    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new Instance().id(INSTANCE_ID)
          .tenantId("college")
          .items(List.of(
            buildItem("Available"),
            buildItem("Checked out"),
            buildItem("In transit"),
            buildItem("Paged"))),
        new Instance().id(INSTANCE_ID)
          .tenantId("university")
          .items(List.of(
            buildItem("Available"),
            buildItem("Checked out"),
            buildItem("Available"),
            buildItem("In transit")))
      ));

    wireMockServer.stubFor(WireMock.get(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    wireMockServer.stubFor(WireMock.post(urlMatching(".*" + INSTANCE_REQUESTS_URL))
      .willReturn(jsonResponse(ecsTlrJson, HttpStatus.SC_CREATED)));

    assertEquals(TENANT, getCurrentTenantId());
    doPost(TLR_URL, ecsTlr)
      .expectStatus().isCreated()
      .expectBody().json(ecsTlrJson);
    assertEquals(TENANT, getCurrentTenantId());

    wireMockServer.verify(getRequestedFor(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT)));

    wireMockServer.verify(postRequestedFor(urlMatching(".*" + INSTANCE_REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo("university")) // because it has most available items
      .withRequestBody(equalToJson(ecsTlrJson)));
  }

  @Test
  void canNotCreateEcsTlrWhenFailedToPickTenant() {
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID);
    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(0)
      .instances(List.of());

    wireMockServer.stubFor(WireMock.get(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isEqualTo(500);

    wireMockServer.verify(getRequestedFor(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT)));
  }

  private String getCurrentTenantId() {
    return context.getTenantId();
  }

  private static Map<String, Collection<String>> buildDefaultHeaders() {
    return new HashMap<>(defaultHeaders().entrySet()
      .stream()
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private FolioExecutionContextSetter initContext() {
    return new FolioExecutionContextSetter(moduleMetadata, buildDefaultHeaders());
  }

  private static EcsTlr buildEcsTlr(String instanceId) {
    return new EcsTlr()
      .id(randomId())
      .itemId(randomId())
      .instanceId(instanceId)
      .requesterId(randomId())
      .pickupServicePointId(randomId())
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestExpirationDate(new Date())
      .requestType(EcsTlr.RequestTypeEnum.PAGE)
      .requestLevel(EcsTlr.RequestLevelEnum.TITLE);
  }

  private static Item buildItem(String status) {
    return new Item()
      .id(randomId())
      .status(new ItemStatus().name(status));
  }

}

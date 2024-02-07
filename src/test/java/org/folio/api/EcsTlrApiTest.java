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
import org.folio.domain.dto.Request;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    String instanceRequestId = randomId();
    String availableItemId = randomId();
    EcsTlr ecsTlr = buildEcsTlr(INSTANCE_ID);
    String ecsTlrJson = asJsonString(ecsTlr);

    SearchInstancesResponse mockSearchInstancesResponse = new SearchInstancesResponse()
      .totalRecords(2)
      .instances(List.of(
        new Instance().id(INSTANCE_ID)
          .tenantId(TENANT_ID_DIKU)
          .items(List.of(
            buildItem(randomId(), TENANT_ID_UNIVERSITY, "Checked out"),
            buildItem(randomId(), TENANT_ID_UNIVERSITY, "In transit"),
            buildItem(availableItemId, TENANT_ID_COLLEGE, "Available")))
      ));

    Request mockInstanceRequestResponse = new Request()
      .id(instanceRequestId)
      .requestLevel(Request.RequestLevelEnum.TITLE)
      .requestType(Request.RequestTypeEnum.PAGE)
      .instanceId(INSTANCE_ID)
      .itemId(availableItemId);

    wireMockServer.stubFor(WireMock.get(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .willReturn(jsonResponse(mockSearchInstancesResponse, HttpStatus.SC_OK)));

    wireMockServer.stubFor(WireMock.post(urlMatching(".*" + INSTANCE_REQUESTS_URL))
      .willReturn(jsonResponse(asJsonString(mockInstanceRequestResponse), HttpStatus.SC_CREATED)));

    EcsTlr expectedPostEcsTlrResponse = fromJsonString(ecsTlrJson, EcsTlr.class)
      .secondaryRequestId(instanceRequestId)
      .secondaryRequestTenantId(TENANT_ID_COLLEGE)
      .itemId(availableItemId);

    assertEquals(TENANT_ID_DIKU, getCurrentTenantId());
    doPost(TLR_URL, ecsTlr)
      .expectStatus().isCreated()
      .expectBody().json(asJsonString(expectedPostEcsTlrResponse), true);
    assertEquals(TENANT_ID_DIKU, getCurrentTenantId());

    wireMockServer.verify(getRequestedFor(urlMatching(".*" + SEARCH_INSTANCES_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_DIKU)));

    wireMockServer.verify(postRequestedFor(urlMatching(".*" + INSTANCE_REQUESTS_URL))
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_COLLEGE)) // because this tenant has available item
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
      .withHeader(TENANT_HEADER, equalTo(TENANT_ID_DIKU)));
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
      .instanceId(instanceId)
      .requesterId(randomId())
      .pickupServicePointId(randomId())
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestExpirationDate(new Date());
  }

  private static Item buildItem(String id, String tenantId, String status) {
    return new Item()
      .id(id)
      .tenantId(tenantId)
      .status(new ItemStatus().name(status));
  }

}

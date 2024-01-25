package org.folio.api;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.EcsTlr;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;

class EcsTlrApiTest extends BaseIT {
  private static final String TLR_URL = "/tlr/ecs-tlr";
  private static final String ANOTHER_TENANT = "university";
  private static final String TENANT_HEADER = "x-okapi-tenant";

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
    EcsTlr ecsTlr = buildEcsTlr();
    wireMockServer.stubFor(WireMock.post(urlMatching(".*/circulation/requests"))
      .withHeader(TENANT_HEADER, equalTo(ANOTHER_TENANT))
      .willReturn(jsonResponse(asJsonString(ecsTlr), HttpStatus.SC_CREATED)));
    assertEquals(TENANT, getCurrentTenantId());

    doPost(TLR_URL, ecsTlr)
      .expectStatus().isCreated()
      .expectBody().json(asJsonString(ecsTlr));

    assertEquals(TENANT, getCurrentTenantId());
    wireMockServer.verify(postRequestedFor(urlMatching(".*/circulation/requests"))
      .withHeader(TENANT_HEADER, equalTo(ANOTHER_TENANT)));
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

  private static EcsTlr buildEcsTlr() {
    return new EcsTlr()
      .id(randomId())
      .itemId(randomId())
      .instanceId(randomId())
      .requesterId(randomId())
      .pickupServicePointId(randomId())
      .fulfillmentPreference(EcsTlr.FulfillmentPreferenceEnum.DELIVERY)
      .patronComments("random comment")
      .requestExpirationDate(new Date())
      .requestType(EcsTlr.RequestTypeEnum.PAGE)
      .requestLevel(EcsTlr.RequestLevelEnum.TITLE);
  }

}

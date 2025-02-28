package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.domain.dto.BatchIds;
import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;
import org.folio.domain.dto.ConsortiumItem;
import org.folio.domain.dto.ConsortiumItems;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

public class CheckOutApiTest extends BaseIT {

  private static final String CHECK_OUT_URL = "/tlr/loans/check-out-by-barcode";
  private static final String CIRCULATION_CHECK_OUT_URL = "/circulation/check-out-by-barcode";
  private static final String SEARCH_ITEMS_URL = "/search/consortium/batch/items";

  private static final String ITEM_ID = randomId();
  private static final String USER_ID = randomId();
  private static final String ITEM_BARCODE = "test_item_barcode";
  private static final String USER_BARCODE = "test_user_barcode";
  private static final UUID SERVICE_POINT_ID = randomUUID();

  @Test
  public void checkOutSuccess() {
    CheckOutRequest checkOutRequest = buildCheckoutRequest();
    CheckOutResponse checkOutResponse = buildCheckOutResponse();

    wireMockServer.stubFor(post(urlEqualTo(CIRCULATION_CHECK_OUT_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(checkOutRequest)))
      .willReturn(jsonResponse(asJsonString(checkOutResponse), HttpStatus.SC_OK)));

    BatchIds itemsSearchRequest = new BatchIds()
      .identifierType(BatchIds.IdentifierTypeEnum.BARCODE)
        .identifierValues(List.of(checkOutRequest.getItemBarcode()));

    ConsortiumItems itemsSearchResponse = new ConsortiumItems()
      .totalRecords(1)
      .items(List.of(new ConsortiumItem()
        .id(ITEM_ID)
        .barcode(ITEM_BARCODE)
        .tenantId(TENANT_ID_COLLEGE)));

    wireMockServer.stubFor(post(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(itemsSearchRequest)))
      .willReturn(jsonResponse(asJsonString(itemsSearchResponse), HttpStatus.SC_OK)));

    checkOut(checkOutRequest)
      .expectStatus().isOk();

    wireMockServer.verify(postRequestedFor(urlEqualTo(CIRCULATION_CHECK_OUT_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));

    wireMockServer.verify(postRequestedFor(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
  }

  @Test
  public void checkOutFailsIfItemIsNotFound() {
    CheckOutRequest checkOutRequest = buildCheckoutRequest();

    BatchIds itemsSearchRequest = new BatchIds()
      .identifierType(BatchIds.IdentifierTypeEnum.BARCODE)
      .identifierValues(List.of(checkOutRequest.getItemBarcode()));

    ConsortiumItems itemsSearchResponse = new ConsortiumItems()
      .totalRecords(0)
      .items(emptyList());

    wireMockServer.stubFor(post(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .withRequestBody(equalToJson(asJsonString(itemsSearchRequest)))
      .willReturn(jsonResponse(asJsonString(itemsSearchResponse), HttpStatus.SC_OK)));

    checkOut(checkOutRequest)
      .expectStatus().is5xxServerError();

    wireMockServer.verify(postRequestedFor(urlEqualTo(SEARCH_ITEMS_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM)));
  }

  private static CheckOutRequest buildCheckoutRequest() {
    return new CheckOutRequest()
      .itemBarcode(ITEM_BARCODE)
      .servicePointId(SERVICE_POINT_ID)
      .userBarcode(USER_BARCODE);
  }

  private static CheckOutResponse buildCheckOutResponse() {
    return new CheckOutResponse()
      .id(randomId())
      .itemId(ITEM_ID)
      .userId(USER_ID);
  }

  private WebTestClient.ResponseSpec checkOut(CheckOutRequest request) {
    return doPost(CHECK_OUT_URL, request);
  }
}

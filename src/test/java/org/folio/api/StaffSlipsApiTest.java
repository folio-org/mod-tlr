package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Items;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Locations;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.client.WireMock;

import lombok.SneakyThrows;

class StaffSlipsApiTest extends BaseIT {

  private static final String SERVICE_POINT_ID = "e0c50666-6144-47b1-9e87-8c1bf30cda34";
  private static final String PICK_SLIPS_URL = "/tlr/staff-slips/pick-slips";
  private static final String LOCATIONS_URL = "/locations";
  private static final String ITEM_STORAGE_URL = "/item-storage/items";
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  private static final String PICK_SLIPS_LOCATION_QUERY =
    "primaryServicePoint==\"" + SERVICE_POINT_ID + "\"";
  private static final String PICK_SLIPS_ITEMS_QUERY_TEMPLATE =
    "status.name==(\"Paged\") and (effectiveLocationId==(%s))";
  private static final String PICK_SLIPS_REQUESTS_QUERY_TEMPLATE =
    "requestType==(\"Page\") and (status==(\"Open - Not yet filled\")) and (itemId==(%s))";

  @Test
  @SneakyThrows
  void pickSlipsAreBuiltSuccessfully() {

    // MOCK LOCATIONS

    Location consortiumLocation = buildLocation();
    Location collegeLocation = buildLocation();

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(new Locations().addLocationsItem(consortiumLocation)))));

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Locations().addLocationsItem(collegeLocation)))));

    // no locations in tenant "university"
    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY))
      .willReturn(okJson(asJsonString(new Locations().locations(emptyList()).totalRecords(0)))));

    // MOCK ITEMS

    Item consortiumItem1 = buildItem(consortiumLocation);
    Item consortiumItem2 = buildItem(consortiumLocation);
    Item collegeItem1 = buildItem(collegeLocation);
    Item collegeItem2 = buildItem(collegeLocation);

    String consortiumItemsQuery = format(PICK_SLIPS_ITEMS_QUERY_TEMPLATE,
      formatIdsForQuery(consortiumLocation.getId()));
    String collegeItemsQuery = format(PICK_SLIPS_ITEMS_QUERY_TEMPLATE,
      formatIdsForQuery(collegeLocation.getId()));

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(ITEM_STORAGE_URL))
      .withQueryParam("query", equalTo(consortiumItemsQuery))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(new Items().items(List.of(consortiumItem1, consortiumItem2))))));

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(ITEM_STORAGE_URL))
      .withQueryParam("query", equalTo(collegeItemsQuery))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Items().items(List.of(collegeItem1, collegeItem2))))));

    // MOCK REQUESTS

    Request consortiumRequest1 = buildRequest(consortiumItem1);
    Request consortiumRequest2 = buildRequest(consortiumItem1);
    Request consortiumRequest3 = buildRequest(consortiumItem2);
    Request consortiumRequest4 = buildRequest(consortiumItem2);
    Requests consortiumRequests = new Requests()
      .requests(List.of(consortiumRequest1, consortiumRequest2, consortiumRequest3, consortiumRequest4));

    Request collegeRequest1 = buildRequest(collegeItem1);
    Request collegeRequest2 = buildRequest(collegeItem1);
    Request collegeRequest3 = buildRequest(collegeItem2);
    Request collegeRequest4 = buildRequest(collegeItem2);
    Requests collegeRequests = new Requests()
      .requests(List.of(collegeRequest1, collegeRequest2, collegeRequest3, collegeRequest4));

    String consortiumRequestsQuery = format(PICK_SLIPS_REQUESTS_QUERY_TEMPLATE,
      formatIdsForQuery(consortiumItem1.getId(), consortiumItem2.getId()));
    String collegeRequestsQuery = format(PICK_SLIPS_REQUESTS_QUERY_TEMPLATE,
      formatIdsForQuery(collegeItem1.getId(), collegeItem2.getId()));

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUEST_STORAGE_URL))
      .withQueryParam("query", equalTo(consortiumRequestsQuery))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(consortiumRequests))));

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUEST_STORAGE_URL))
      .withQueryParam("query", equalTo(collegeRequestsQuery))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(collegeRequests))));

    // GET PICK SLIPS

    getPickSlips()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("pickSlips").value(hasSize(8))
      .jsonPath("totalRecords").value(is(8))
      .jsonPath("pickSlips[*].currentDateTime").exists()
      .jsonPath("pickSlips[*].item").exists()
      .jsonPath("pickSlips[*].request").exists();

    wireMockServer.verify(0, getRequestedFor(urlPathMatching(ITEM_STORAGE_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));
    wireMockServer.verify(0, getRequestedFor(urlPathMatching(REQUEST_STORAGE_URL))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_UNIVERSITY)));
  }

  private WebTestClient.ResponseSpec getPickSlips() {
    return getPickSlips(SERVICE_POINT_ID);
  }

  @SneakyThrows
  private WebTestClient.ResponseSpec getPickSlips(String servicePointId) {
    return doGet(PICK_SLIPS_URL + "/" + servicePointId);
  }

  private static Location buildLocation() {
    return new Location()
      .id(randomId())
      .primaryServicePoint(UUID.fromString(SERVICE_POINT_ID));
  }

  private static Item buildItem(Location location) {
    return new Item()
      .id(randomId())
      .status(new ItemStatus(ItemStatus.NameEnum.PAGED))
      .effectiveLocationId(location.getId());
  }

  private static Request buildRequest(Item item) {
    return new Request()
      .id(randomId())
      .itemId(item.getId());
  }

  private static String formatIdsForQuery(String... ids) {
    return Arrays.stream(ids)
      .map(id -> "\"" + id + "\"")
      .collect(joining(" or "));
  }
}

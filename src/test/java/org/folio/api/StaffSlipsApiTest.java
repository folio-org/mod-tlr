package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Items;
import org.folio.domain.dto.Location;
import org.folio.domain.dto.Locations;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.github.tomakehurst.wiremock.client.WireMock;

import lombok.SneakyThrows;

public class StaffSlipsApiTest extends BaseIT {

  private static final String SERVICE_POINT_ID = "e0c50666-6144-47b1-9e87-8c1bf30cda34";
  private static final String PICK_SLIPS_URL = "/tlr/staff-slips/pick-slips";
  private static final String LOCATIONS_URL = "/locations";
  private static final String ITEM_STORAGE_URL = "/item-storage/items";
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  private static final String PICK_SLIPS_LOCATION_QUERY =
    "primaryServicePoint==\"" + SERVICE_POINT_ID + "\"";
  private static final String PICK_SLIPS_ITEMS_QUERY_TEMPLATE =
    "status.name==(\"Paged\") and (effectiveLocationId==(\"%s\"))";
  private static final String PICK_SLIPS_REQUESTS_QUERY_TEMPLATE =
    "requestType==(\"Page\") and (status==(\"Open - Not yet filled\")) and (itemId==(\"%s\"))";

  @Test
  @SneakyThrows
  void pickSlipsAreBuiltSuccessfully() {
    String locationId = randomId();
    Location location = new Location()
      .id(locationId)
      .primaryServicePoint(UUID.fromString(SERVICE_POINT_ID));

    // mock location in tenant "college"
    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Locations().addLocationsItem(location)))));

    // no locations in other tenants
    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(LOCATIONS_URL))
      .withQueryParam("query", equalTo(PICK_SLIPS_LOCATION_QUERY))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_COLLEGE)))
      .willReturn(okJson(asJsonString(new Locations().locations(emptyList()).totalRecords(0)))));

    Item item = new Item()
      .id(randomId())
      .status(new ItemStatus(ItemStatus.NameEnum.PAGED))
      .effectiveLocationId(locationId);

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(ITEM_STORAGE_URL))
      .withQueryParam("query", equalTo(String.format(PICK_SLIPS_ITEMS_QUERY_TEMPLATE, locationId)))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Items().addItemsItem(item)))));

    Request request = new Request()
      .id(randomId())
      .itemId(item.getId());

    wireMockServer.stubFor(WireMock.get(urlPathEqualTo(REQUEST_STORAGE_URL))
      .withQueryParam("query", equalTo(String.format(PICK_SLIPS_REQUESTS_QUERY_TEMPLATE, item.getId())))
      .withQueryParam("limit", equalTo("1000"))
      .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Requests().addRequestsItem(request)))));

    getPickSlips()
      .andExpect(status().isOk())
      .andExpect(jsonPath("pickSlips", hasSize(1)))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("pickSlips[0].item").exists())
      .andExpect(jsonPath("pickSlips[0].request").exists());

    wireMockServer.verify(0, getRequestedFor(urlPathMatching(ITEM_STORAGE_URL))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_COLLEGE))));
    wireMockServer.verify(0, getRequestedFor(urlPathMatching(REQUEST_STORAGE_URL))
      .withHeader(HEADER_TENANT, not(equalTo(TENANT_ID_COLLEGE))));
  }

  private ResultActions getPickSlips() {
    return getPickSlips(SERVICE_POINT_ID);
  }

  @SneakyThrows
  private ResultActions getPickSlips(String servicePointId) {
    return mockMvc.perform(
      get(PICK_SLIPS_URL + "/" + servicePointId)
        .headers(defaultHeaders())
        .contentType(MediaType.APPLICATION_JSON));
  }
}

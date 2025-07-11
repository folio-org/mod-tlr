package org.folio.util;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.http.HttpStatus;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.DefaultKafkaEvent;
import org.folio.support.kafka.KafkaEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import com.github.tomakehurst.wiremock.WireMockServer;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {

  @SneakyThrows
  public static String buildToken(String tenantId) {
    JSONObject header = new JSONObject()
      .put("alg", "HS256");

    JSONObject payload = new JSONObject()
      .put("sub", tenantId + "_admin")
      .put("user_id", "bb6a6f19-9275-4261-ad9d-6c178c24c4fb")
      .put("type", "access")
      .put("exp", 1708342543)
      .put("iat", 1708341943)
      .put("tenant", tenantId);

    String signature = "De_0um7P_Rv-diqjHKLcSHZdjzjjshvlBbi6QPrz0Tw";

    return String.format("%s.%s.%s",
      Base64.getEncoder().encodeToString(header.toString().getBytes()),
      Base64.getEncoder().encodeToString(payload.toString().getBytes()),
      signature);
  }

  @SneakyThrows
  public static void mockUserTenants(WireMockServer wireMockServer, String tenantId,
    UUID consortiumId) {

    wireMockServer.stubFor(get(urlEqualTo("/user-tenants?limit=1"))
      .willReturn(okJson(new JSONObject()
        .put("totalRecords", 1)
        .put("userTenants", new JSONArray()
          .put(new JSONObject()
            .put("centralTenantId", tenantId)
            .put("consortiumId", consortiumId)
            .put("userId", UUID.randomUUID().toString())
            .put("tenantId", UUID.randomUUID().toString())))
        .toString())));
  }

  @SneakyThrows
  public static void mockConsortiaTenants(WireMockServer wireMockServer, UUID consortiumId) {
    wireMockServer.stubFor(get(urlEqualTo(format("/consortia/%s/tenants", consortiumId)))
      .willReturn(jsonResponse(new JSONObject()
        .put("tenants", new JSONArray(Set.of(
          new JSONObject().put("id", "consortium").put("isCentral", "true"),
          new JSONObject().put("id", "university").put("isCentral", "false"),
          new JSONObject().put("id", "college").put("isCentral", "false")
        ))).toString(), HttpStatus.SC_OK)));
  }

  public static <T> KafkaEvent<T> buildEvent(String tenant,
    DefaultKafkaEvent.DefaultKafkaEventType type, T oldVersion, T newVersion) {

    DefaultKafkaEvent.DefaultKafkaEventData<T> data =
      DefaultKafkaEvent.DefaultKafkaEventData.<T>builder()
        .oldVersion(oldVersion)
        .newVersion(newVersion)
        .build();

    return buildEvent(tenant, type, data);
  }

  private static <T> KafkaEvent<T> buildEvent(String tenant,
    DefaultKafkaEvent.DefaultKafkaEventType type, DefaultKafkaEvent.DefaultKafkaEventData<T> data) {

    return DefaultKafkaEvent.<T>builder()
      .id(randomId())
      .timestamp(new Date().getTime())
      .tenant(tenant)
      .type(type)
      .data(data)
      .build()
      .withTenantIdHeaderValue(tenant);
  }

  public static void mockSystemUserService(SystemUserScopedExecutionService systemUserService) {
    doAnswer(invocation -> ((Callable<?>) invocation.getArguments()[1]).call())
      .when(systemUserService).executeSystemUserScoped(anyString(), any(Callable.class));
  }

  public static String randomId() {
    return UUID.randomUUID().toString();
  }
}

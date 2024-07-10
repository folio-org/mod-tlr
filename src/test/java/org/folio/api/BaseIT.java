package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.util.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.WireMockServer;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BaseIT.DockerPostgresDataSourceInitializer.class)
@TestPropertySource(properties = {
  "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers
@DirtiesContext
@Log4j2
public class BaseIT {
  private static final String FOLIO_ENVIRONMENT = "folio";
  protected static final String HEADER_TENANT = "x-okapi-tenant";
  protected static final String TOKEN = "test_token";
  protected static final String TENANT_ID_CONSORTIUM = "consortium"; // central tenant
  protected static final String TENANT_ID_UNIVERSITY = "university";
  protected static final String TENANT_ID_COLLEGE = "college";
  protected static final String REQUEST_KAFKA_TOPIC_NAME =
    buildTopicName("circulation", "request");
  protected static final String USER_GROUP_KAFKA_TOPIC_NAME =
    buildTopicName("users", "userGroup");
  private static final String[] KAFKA_TOPICS = {
    REQUEST_KAFKA_TOPIC_NAME,
    USER_GROUP_KAFKA_TOPIC_NAME
  };
  private static final int WIRE_MOCK_PORT = TestSocketUtils.findAvailableTcpPort();
  protected static WireMockServer wireMockServer = new WireMockServer(WIRE_MOCK_PORT);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private static final String CENTRAL_TENANT_ID = TENANT_ID_CONSORTIUM;
  private static final UUID CONSORTIUM_ID = randomUUID();

  @Autowired
  private WebTestClient webClient;
  @Autowired
  private FolioExecutionContext context;
  @Autowired
  private FolioModuleMetadata moduleMetadata;
  private FolioExecutionContextSetter contextSetter;
  protected static AdminClient kafkaAdminClient;
  @Autowired
  protected KafkaTemplate<String, String> kafkaTemplate;

  @Container
  private static final PostgreSQLContainer<?> postgresDBContainer =
    new PostgreSQLContainer<>("postgres:12-alpine");
  @Container
  private static final KafkaContainer kafka = new KafkaContainer(
    DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("folio.okapi-url", wireMockServer::baseUrl);
  }

  @BeforeEach
  void beforeEachTest() {
    doPost("/_/tenant", asJsonString(new TenantAttributes().moduleTo("mod-tlr")))
      .expectStatus().isNoContent();

    contextSetter = initFolioContext();
    wireMockServer.resetAll();
  }

  @AfterEach
  public void afterEachTest() {
    contextSetter.close();
  }

  @BeforeAll
  static void setUp() {
    wireMockServer = new WireMockServer(WIRE_MOCK_PORT);
    wireMockServer.start();

    kafkaAdminClient = KafkaAdminClient.create(Map.of(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    createKafkaTopics(KAFKA_TOPICS);
  }

  @AfterAll
  static void tearDown() {
    wireMockServer.stop();
    kafkaAdminClient.close();
  }

  public static String getOkapiUrl() {
    return String.format("http://localhost:%s", WIRE_MOCK_PORT);
  }

  @SneakyThrows
  protected static void setUpTenant(MockMvc mockMvc) {
    mockMvc.perform(MockMvcRequestBuilders.post("/_/tenant")
      .content(asJsonString(new TenantAttributes().moduleTo("mod-tlr")))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON)).andExpect(status().isNoContent());
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.put(XOkapiHeaders.TENANT, List.of(TENANT_ID_CONSORTIUM));
    httpHeaders.add(XOkapiHeaders.URL, wireMockServer.baseUrl());
    httpHeaders.add(XOkapiHeaders.TOKEN, TOKEN);
    httpHeaders.add(XOkapiHeaders.USER_ID, "08d51c7a-0f36-4f3d-9e35-d285612a23df");

    return httpHeaders;
  }

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  @SneakyThrows
  public static <T> T fromJsonString(String json, Class<T> objectType) {
    return OBJECT_MAPPER.readValue(json, objectType);
  }

  public static class DockerPostgresDataSourceInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
        "spring.datasource.url=" + postgresDBContainer.getJdbcUrl(),
        "spring.datasource.username=" + postgresDBContainer.getUsername(),
        "spring.datasource.password=" + postgresDBContainer.getPassword());
    }
  }

  protected WebTestClient.RequestBodySpec buildRequest(HttpMethod method, String uri) {
    return webClient.method(method)
      .uri(uri)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID_CONSORTIUM)
      .header(XOkapiHeaders.URL, wireMockServer.baseUrl())
      .header(XOkapiHeaders.TOKEN, TOKEN)
      .header(XOkapiHeaders.USER_ID, randomId());
  }

  protected WebTestClient.ResponseSpec doGet(String url) {
    return buildRequest(HttpMethod.GET, url)
      .exchange();
  }

  protected WebTestClient.ResponseSpec doPost(String url, Object payload) {
    return doPostWithTenant(url, payload, TENANT_ID_CONSORTIUM);
  }

  protected WebTestClient.ResponseSpec doPostWithTenant(String url, Object payload, String tenantId) {
    return doPostWithToken(url, payload, TestUtils.buildToken(tenantId));
  }

  protected WebTestClient.ResponseSpec doPostWithToken(String url, Object payload, String token) {
    return buildRequest(HttpMethod.POST, url)
      .cookie("folioAccessToken", token)
      .body(BodyInserters.fromValue(payload))
      .exchange();
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }

  private static Map<String, Collection<String>> buildDefaultHeaders() {
    return new HashMap<>(defaultHeaders().entrySet()
      .stream()
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  protected FolioExecutionContextSetter initFolioContext() {
    return new FolioExecutionContextSetter(moduleMetadata, buildDefaultHeaders());
  }

  protected String getCurrentTenantId() {
    return context.getTenantId();
  }

  @SneakyThrows
  private static void createKafkaTopics(String... topicNames) {
    List<NewTopic> topics = Arrays.stream(topicNames)
      .map(topic -> new NewTopic(topic, 1, (short) 1))
      .toList();

    kafkaAdminClient.createTopics(topics)
      .all()
      .get(10, TimeUnit.SECONDS);
  }

  protected static String buildTopicName(String module, String objectType) {
    return buildTopicName(FOLIO_ENVIRONMENT, TENANT_ID_CONSORTIUM, module, objectType);
  }

  private static String buildTopicName(String env, String tenant, String module, String objectType) {
    return String.format("%s.%s.%s.%s", env, tenant, module, objectType);
  }

  protected MessageHeaders getMessageHeaders(String tenantName, String tenantId) {
    Map<String, Object> header = new HashMap<>();
    header.put(XOkapiHeaders.TENANT, tenantName.getBytes());
    header.put("folio.tenantId", tenantId);

    return new MessageHeaders(header);
  }

  @SneakyThrows
  protected void mockUserTenants() {
    wireMockServer.stubFor(get(urlEqualTo("/user-tenants?limit=1"))
      .willReturn(okJson(new JSONObject()
        .put("totalRecords", 1)
        .put("userTenants", new JSONArray()
          .put(new JSONObject()
            .put("centralTenantId", CENTRAL_TENANT_ID)
            .put("consortiumId", CONSORTIUM_ID)
            .put("userId", UUID.randomUUID().toString())
            .put("tenantId", UUID.randomUUID().toString())))
        .toString())));
  }

  @SneakyThrows
  protected void mockConsortiaTenants() {
    wireMockServer.stubFor(get(urlEqualTo(format("/consortia/%s/tenants", CONSORTIUM_ID)))
      .willReturn(jsonResponse(new JSONObject()
        .put("tenants", new JSONArray(Set.of(
          new JSONObject().put("id", "consortium").put("isCentral", "true"),
          new JSONObject().put("id", "university").put("isCentral", "false"),
          new JSONObject().put("id", "college").put("isCentral", "false")
        ))).toString(), HttpStatus.SC_OK)));
  }
}

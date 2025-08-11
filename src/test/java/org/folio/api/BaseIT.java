package org.folio.api;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.service.impl.ConsortiumServiceImpl;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.util.TestUtils;
import org.jetbrains.annotations.NotNull;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
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
  protected static final String USED_ID = "08d51c7a-0f36-4f3d-9e35-d285612a23df";
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
      DockerImageName.parse("apache/kafka-native:3.8.0"))
      .withStartupAttempts(3);

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
    ConsortiumServiceImpl.clearCache();
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
      .headers(defaultHeadersForRequest())
      .contentType(APPLICATION_JSON)).andExpect(status().isNoContent());
  }

  public static HttpHeaders defaultHeadersForRequest() {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(APPLICATION_JSON);
    buildHeaders().forEach(httpHeaders::add);
    return httpHeaders;
  }

  protected static Collection<Header> buildHeadersForKafkaProducer(String tenant) {
    return buildKafkaHeaders(tenant)
      .entrySet()
      .stream()
      .map(entry -> new RecordHeader(entry.getKey(), (byte[]) entry.getValue()))
      .collect(toList());
  }

  protected static Map<String, Object> buildKafkaHeaders(String tenantId) {
    Map<String, String> headers = buildHeaders(tenantId);
    headers.put("folio.tenantId", tenantId);

    return headers.entrySet()
      .stream()
      .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes()));
  }

  protected static Map<String, String> buildHeaders() {
    return buildHeaders(TENANT_ID_CONSORTIUM);
  }

  protected static Map<String, String> buildHeaders(String tenantId) {
    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenantId);
    headers.put(XOkapiHeaders.URL, wireMockServer.baseUrl());
    headers.put(XOkapiHeaders.TOKEN, TOKEN);
    headers.put(XOkapiHeaders.USER_ID, USED_ID);
    headers.put(XOkapiHeaders.REQUEST_ID, randomId());
    return headers;
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

  protected WebTestClient.ResponseSpec doPostWithHeaders(String url, Object payload,
    String requestId, String permissions, String tenantId) {

    return webClient.method(HttpMethod.POST)
      .uri(url)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, tenantId)
      .header(XOkapiHeaders.PERMISSIONS, permissions)
      .header(XOkapiHeaders.REQUEST_ID, requestId)
      .header(XOkapiHeaders.URL, wireMockServer.baseUrl())
      .header(XOkapiHeaders.USER_ID, randomId())
      .cookie("folioAccessToken", TestUtils.buildToken(tenantId))
      .body(BodyInserters.fromValue(payload))
      .exchange();
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }

  private static Map<String, Collection<String>> buildDefaultHeaders() {
    return new HashMap<>(defaultHeadersForRequest().entrySet()
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

}

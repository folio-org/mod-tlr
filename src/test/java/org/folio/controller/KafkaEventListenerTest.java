package org.folio.controller;

import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.api.BaseIT;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import static org.awaitility.Awaitility.await;
import static org.folio.support.MockDataUtils.ITEM_ID;
import static org.folio.support.MockDataUtils.SECONDARY_REQUEST_ID;
import static org.folio.support.MockDataUtils.getEcsTlrEntity;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@EmbeddedKafka(
  topics = {KafkaEventListenerTest.REQUEST_TOPIC_NAME},
  brokerProperties = {
    "listeners=PLAINTEXT://localhost:9092",
    "port=9092"
  }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaEventListenerTest extends BaseIT {
  private static final String ENV = "folio";
  private static final Duration ASYNC_AWAIT_TIMEOUT = Duration.ofSeconds(60);
  public static final String REQUEST_TOPIC_NAME = "folio.diku.circulation.request";
  private static final String REQUEST_UPDATE_EVENT_SAMPLE = getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");
  protected EmbeddedKafkaBroker embeddedKafkaBroker;
  @SpyBean
  private KafkaEventListener listener;
  protected KafkaTemplate<String, String> kafkaTemplate;

  @SpyBean
  private EcsTlrRepository ecsTlrRepository;

  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  public KafkaEventListenerTest(@Autowired SystemUserScopedExecutionService systemUserScopedExecutionService,
                                @Autowired EmbeddedKafkaBroker embeddedKafkaBroker) {
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
    this.embeddedKafkaBroker = embeddedKafkaBroker;
  }

  @BeforeAll
  public void setUp() {
    kafkaTemplate = buildKafkaTemplate();
  }

  private KafkaTemplate<String, String> buildKafkaTemplate() {
    var senderProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    senderProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    senderProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(senderProps));
  }

  @Test
  void requestEventIsConsumed() {
    kafkaTemplate.send(new ProducerRecord(REQUEST_TOPIC_NAME, String.valueOf(UUID.randomUUID()), "test_message"));
    await().atMost(ASYNC_AWAIT_TIMEOUT).untilAsserted(() ->
      verify(listener).handleRequestEvent(any(), any()));
  }

  @Test
  void requestUpdateEventIsConsumed() {
    systemUserScopedExecutionService.executeAsyncSystemUserScoped(TENANT_ID_CONSORTIUM, () ->
        ecsTlrRepository.save(getEcsTlrEntity()));

    Message<String> message = MessageBuilder.withPayload(REQUEST_UPDATE_EVENT_SAMPLE)
      .setHeader(KafkaHeaders.TOPIC, REQUEST_TOPIC_NAME)
      .setHeader(XOkapiHeaders.TENANT, TENANT_ID_CONSORTIUM.getBytes())
      .build();

    kafkaTemplate.send(message);
    await().atMost(ASYNC_AWAIT_TIMEOUT).untilAsserted(() ->
        verify(ecsTlrRepository).save(any())
    );

    await().atMost(ASYNC_AWAIT_TIMEOUT).untilAsserted(() ->
      systemUserScopedExecutionService.executeAsyncSystemUserScoped(TENANT_ID_CONSORTIUM, () -> {
        var updatedEcsTlr = ecsTlrRepository.findBySecondaryRequestId(SECONDARY_REQUEST_ID);
        assert(updatedEcsTlr).isPresent();
        Assertions.assertEquals(updatedEcsTlr.get().getItemId(), ITEM_ID);
      }));
  }

  private static String buildTopicName(String module, String objectType) {
    return buildTopicName(ENV, TENANT_ID_CONSORTIUM, module, objectType);
  }

  private static String buildTopicName(String env, String tenant, String module, String objectType) {
    return String.format("%s.%s.%s.%s", env, tenant, module, objectType);
  }

}

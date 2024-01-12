package org.folio.controller;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import lombok.SneakyThrows;

@SpringBootTest
@Testcontainers
class KafkaEventListenerTest {
  private static final String ENV = "folio";
  private static final String TENANT = "test_tenant";
  private static final String REQUEST_TOPIC_NAME = buildTopicName("circulation", "request");
  private static final String CONSUMER_GROUP_ID = "folio-mod-tlr-group";

  private static KafkaProducer<String, String> kafkaProducer;
  private static AdminClient kafkaAdminClient;

  @Container
  private static final KafkaContainer kafka = new KafkaContainer(
    DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @BeforeAll
  public static void beforeClass() {
    kafkaProducer = new KafkaProducer<>(Map.of(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
    ));

    kafkaAdminClient = KafkaAdminClient.create(Map.of(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
  }

  @AfterAll
  public static void afterClass() {
    kafkaProducer.close();
    kafkaAdminClient.close();
  }

  @Test
  void requestEventIsConsumed() {
    publishEventAndWait(REQUEST_TOPIC_NAME, CONSUMER_GROUP_ID, "test message");
  }

  @SneakyThrows
  private static int getOffset(String topic, String consumerGroup) {
      return kafkaAdminClient.listConsumerGroupOffsets(consumerGroup)
        .partitionsToOffsetAndMetadata()
        .thenApply(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::offset)
        .map(Long::intValue)
        .orElse(0))
        .get(10, TimeUnit.SECONDS);
  }

  private static void publishEventAndWait(String topic, String consumerGroupId, String payload) {
    final int initialOffset = getOffset(topic, consumerGroupId);
    publishEvent(topic, payload);
    waitForOffset(topic, consumerGroupId, initialOffset + 1);
  }

  private static void publishEvent(String topic, String payload) {
    kafkaProducer.send(new ProducerRecord<>(topic, payload));
  }

  private static void waitForOffset(String topic, String consumerGroupId, int expectedOffset) {
    Awaitility.await()
      .atMost(30, TimeUnit.SECONDS)
      .until(() -> getOffset(topic, consumerGroupId), offset -> offset.equals(expectedOffset));
  }

  private static String buildTopicName(String module, String objectType) {
    return buildTopicName(ENV, TENANT, module, objectType);
  }

  private static String buildTopicName(String env, String tenant, String module, String objectType) {
    return String.format("%s.%s.%s.%s", env, tenant, module, objectType);
  }

}
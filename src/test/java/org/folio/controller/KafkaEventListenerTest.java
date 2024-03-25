package org.folio.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.support.MockDataUtils.ITEM_ID;
import static org.folio.support.MockDataUtils.PRIMARY_REQUEST_ID;
import static org.folio.support.MockDataUtils.SECONDARY_REQUEST_ID;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.awaitility.Awaitility;
import org.folio.api.BaseIT;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.TransactionStatusResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.client.WireMock;

import lombok.SneakyThrows;

class KafkaEventListenerTest extends BaseIT {
  private static final String UUID_PATTERN =
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
  private static final String ECS_REQUEST_TRANSACTIONS_URL = "/ecs-request-transactions";
  private static final String POST_ECS_REQUEST_TRANSACTION_URL_PATTERN =
    ECS_REQUEST_TRANSACTIONS_URL + "/" + UUID_PATTERN;
  private static final String REQUEST_TOPIC_NAME = buildTopicName("circulation", "request");
  private static final String REQUEST_UPDATE_EVENT_SAMPLE =
    getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");
  private static final String CONSUMER_GROUP_ID = "folio-mod-tlr-group";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;
  @Autowired
  private SystemUserScopedExecutionService executionService;

  @BeforeEach
  void beforeEach() {
    ecsTlrRepository.deleteAll();
  }

  @Test
  void requestUpdateEventIsConsumed() {
    EcsTlrEntity newEcsTlr = EcsTlrEntity.builder()
      .id(UUID.randomUUID())
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .primaryRequestTenantId(TENANT_ID_CONSORTIUM)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .secondaryRequestTenantId(TENANT_ID_COLLEGE)
      .build();

    EcsTlrEntity initialEcsTlr = executionService.executeSystemUserScoped(TENANT_ID_CONSORTIUM,
      () -> ecsTlrRepository.save(newEcsTlr));
    assertNull(initialEcsTlr.getItemId());

    var mockEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CREATED);
    wireMockServer.stubFor(WireMock.post(urlMatching(".*" + POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .willReturn(jsonResponse(mockEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    publishEvent(REQUEST_TOPIC_NAME, REQUEST_UPDATE_EVENT_SAMPLE);

    EcsTlrEntity updatedEcsTlr = executionService.executeSystemUserScoped(TENANT_ID_CONSORTIUM,
      () -> Awaitility.await()
        .atMost(30, SECONDS)
        .until(() -> ecsTlrRepository.findById(initialEcsTlr.getId()),
          ecsTlr -> ecsTlr.isPresent() && ITEM_ID.equals(ecsTlr.get().getItemId()))
    ).orElseThrow();

    verifyDcbTransactions(updatedEcsTlr);
  }

  @Test
  void requestUpdateEventIsIgnoredWhenEcsTlrAlreadyHasItemId() {
    UUID ecsTlrId = UUID.randomUUID();
    EcsTlrEntity initialEcsTlr = EcsTlrEntity.builder()
      .id(ecsTlrId)
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .itemId(ITEM_ID)
      .build();

    executionService.executeAsyncSystemUserScoped(TENANT_ID_CONSORTIUM,
      () -> ecsTlrRepository.save(initialEcsTlr));

    var mockEcsDcbTransactionResponse = new TransactionStatusResponse()
      .status(TransactionStatusResponse.StatusEnum.CREATED);

    wireMockServer.stubFor(WireMock.post(urlMatching(".*" + POST_ECS_REQUEST_TRANSACTION_URL_PATTERN))
      .willReturn(jsonResponse(mockEcsDcbTransactionResponse, HttpStatus.SC_CREATED)));

    publishEventAndWait(REQUEST_TOPIC_NAME, CONSUMER_GROUP_ID, REQUEST_UPDATE_EVENT_SAMPLE);

    EcsTlrEntity ecsTlr = executionService.executeSystemUserScoped(TENANT_ID_CONSORTIUM,
      () -> ecsTlrRepository.findById(ecsTlrId)).orElseThrow();
    assertEquals(ITEM_ID, ecsTlr.getItemId());
    assertNull(ecsTlr.getPrimaryRequestDcbTransactionId());
    assertNull(ecsTlr.getSecondaryRequestDcbTransactionId());
    wireMockServer.verify(exactly(0), postRequestedFor(urlMatching(
      ".*" + POST_ECS_REQUEST_TRANSACTION_URL_PATTERN)));
  }

  private static void verifyDcbTransactions(EcsTlrEntity ecsTlr) {
//    UUID primaryRequestDcbTransactionId = ecsTlr.getPrimaryRequestDcbTransactionId();
    UUID secondaryRequestDcbTransactionId = ecsTlr.getSecondaryRequestDcbTransactionId();
//    assertNotNull(primaryRequestDcbTransactionId);
    assertNotNull(secondaryRequestDcbTransactionId);

//    DcbTransaction expectedBorrowerTransaction = new DcbTransaction()
//      .role(DcbTransaction.RoleEnum.BORROWER)
//      .requestId(ecsTlr.getPrimaryRequestId().toString());

    DcbTransaction expectedLenderTransaction = new DcbTransaction()
      .role(DcbTransaction.RoleEnum.LENDER)
      .requestId(ecsTlr.getSecondaryRequestId().toString());

//    wireMockServer.verify(
//      postRequestedFor(urlMatching(
//        ".*" + ECS_REQUEST_TRANSACTIONS_URL + "/" + primaryRequestDcbTransactionId))
//        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_CONSORTIUM))
//        .withRequestBody(equalToJson(asJsonString(expectedBorrowerTransaction))));

    wireMockServer.verify(
      postRequestedFor(urlMatching(
        ".*" + ECS_REQUEST_TRANSACTIONS_URL + "/" + secondaryRequestDcbTransactionId))
        .withHeader(HEADER_TENANT, equalTo(TENANT_ID_COLLEGE))
        .withRequestBody(equalToJson(asJsonString(expectedLenderTransaction))));
  }

  @SneakyThrows
  private void publishEvent(String topic, String payload) {
    kafkaTemplate.send(topic, randomId(), payload)
      .get(10, SECONDS);
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

  private  void publishEventAndWait(String topic, String consumerGroupId, String payload) {
    final int initialOffset = getOffset(topic, consumerGroupId);
    publishEvent(topic, payload);
    waitForOffset(topic, consumerGroupId, initialOffset + 1);
  }

  private void waitForOffset(String topic, String consumerGroupId, int expectedOffset) {
    Awaitility.await()
      .atMost(60, TimeUnit.SECONDS)
      .until(() -> getOffset(topic, consumerGroupId), offset -> offset.equals(expectedOffset));
  }

}

package org.folio.controller;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.support.MockDataUtils.ITEM_ID;
import static org.folio.support.MockDataUtils.SECONDARY_REQUEST_ID;
import static org.folio.support.MockDataUtils.getEcsTlrEntity;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.awaitility.Awaitility;
import org.folio.api.BaseIT;
import org.folio.repository.EcsTlrRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.SneakyThrows;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class KafkaEventListenerTest extends BaseIT {
  private static final String REQUEST_TOPIC_NAME = buildTopicName("circulation", "request");
  private static final String REQUEST_UPDATE_EVENT_SAMPLE =
    getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");

  @Autowired
  private EcsTlrRepository ecsTlrRepository;
  @Autowired
  private SystemUserScopedExecutionService executionService;

  @Test
  void requestUpdateEventIsConsumed() {
    executionService.executeAsyncSystemUserScoped(TENANT_ID_CONSORTIUM,
      () ->  ecsTlrRepository.save(getEcsTlrEntity()));

    publishEvent(REQUEST_TOPIC_NAME, REQUEST_UPDATE_EVENT_SAMPLE);

    Awaitility.await()
      .atMost(30, SECONDS)
      .untilAsserted(() ->
        executionService.executeAsyncSystemUserScoped(TENANT_ID_CONSORTIUM, () -> {
          var updatedEcsTlr = ecsTlrRepository.findBySecondaryRequestId(SECONDARY_REQUEST_ID);
          assertTrue(updatedEcsTlr.isPresent());
          assertEquals(ITEM_ID, updatedEcsTlr.get().getItemId());
        })
      );
  }

  @SneakyThrows
  private void publishEvent(String topic, String payload) {
    List<Header> headers = new ArrayList<>();
    headers.add(new RecordHeader(XOkapiHeaders.TENANT,
      TENANT_ID_CONSORTIUM.getBytes(StandardCharsets.UTF_8)));
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, null,
      randomId(), payload, headers);
    kafkaTemplate.send(record).get(10, SECONDS);
  }
}

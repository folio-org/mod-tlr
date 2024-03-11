package org.folio.service;

import static org.folio.support.MockDataUtils.getEcsTlrEntity;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.folio.api.BaseIT;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.EcsTlrServiceImpl;
import org.folio.service.impl.KafkaEventHandlerImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class KafkaEventHandlerImplTest extends BaseIT {
  private static final String REQUEST_UPDATE_EVENT_SAMPLE = getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");

  @InjectMocks
  private KafkaEventHandlerImpl eventHandler;

  @InjectMocks
  private EcsTlrServiceImpl ecsTlrService;

  @MockBean
  private EcsTlrRepository ecsTlrRepository;

  @Autowired
  private KafkaEventListener eventListener;

  @Test
  void handleRequestUpdateTest() {
    when(ecsTlrRepository.findBySecondaryRequestId(any())).thenReturn(Optional.of(getEcsTlrEntity()));
    eventListener.handleRequestEvent(REQUEST_UPDATE_EVENT_SAMPLE);
    verify(ecsTlrRepository).save(any());
  }

  @Test
  void handleRequestEventWithoutItemIdTest() {
    when(ecsTlrRepository.findBySecondaryRequestId(any())).thenReturn(Optional.of(getEcsTlrEntity()));
    eventListener.handleRequestEvent(REQUEST_UPDATE_EVENT_SAMPLE);
    verify(ecsTlrRepository).save(any());
  }
}

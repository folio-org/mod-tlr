package org.folio.service;

import static org.folio.support.MockDataUtils.getEcsTlrEntity;
import static org.folio.support.MockDataUtils.getMockDataAsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.folio.api.BaseIT;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.repository.EcsTlrRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class RequestEventHandlerTest extends BaseIT {
  private static final String REQUEST_UPDATE_EVENT_SAMPLE =
    getMockDataAsString("mockdata/kafka/secondary_request_update_event.json");

  @MockBean
  private DcbService dcbService;
  @MockBean
  RequestService requestService;
  @MockBean
  private EcsTlrRepository ecsTlrRepository;

  @Autowired
  private KafkaEventListener eventListener;

  @Test
  void handleRequestUpdateTest() {
    when(ecsTlrRepository.findBySecondaryRequestId(any())).thenReturn(Optional.of(getEcsTlrEntity()));
    doNothing().when(dcbService).createLendingTransaction(any());
    eventListener.handleRequestEvent(REQUEST_UPDATE_EVENT_SAMPLE, buildKafkaHeaders(TENANT_ID_CONSORTIUM));
    verify(ecsTlrRepository).findBySecondaryRequestId(any());
  }
}

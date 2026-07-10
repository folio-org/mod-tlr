package org.folio.service.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.client.CancellationReasonClient;
import org.folio.domain.dto.CancellationReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancellationReasonServiceImplTest {

  private static final String CANCELLATION_REASON_ID = "50ed35b2-1397-4e83-a76b-642adf91ca2a";

  @Mock
  private CancellationReasonClient cancellationReasonClient;

  @InjectMocks
  private CancellationReasonServiceImpl cancellationReasonService;

  @Test
  void findReturnsCancellationReasonFetchedFromClient() {
    CancellationReason cancellationReason = new CancellationReason()
      .id(CANCELLATION_REASON_ID)
      .name("Shared reason")
      .source("Consortium");

    when(cancellationReasonClient.getCancellationReason(CANCELLATION_REASON_ID))
      .thenReturn(cancellationReason);

    CancellationReason result = cancellationReasonService.find(CANCELLATION_REASON_ID);

    assertThat(result, is(cancellationReason));
    verify(cancellationReasonClient).getCancellationReason(CANCELLATION_REASON_ID);
  }

  @Test
  void findReturnsNullWhenClientReturnsNull() {
    when(cancellationReasonClient.getCancellationReason(CANCELLATION_REASON_ID))
      .thenReturn(null);

    CancellationReason result = cancellationReasonService.find(CANCELLATION_REASON_ID);

    assertThat(result, is(nullValue()));
  }

  @Test
  void findPropagatesExceptionThrownByClient() {
    when(cancellationReasonClient.getCancellationReason(CANCELLATION_REASON_ID))
      .thenThrow(new RuntimeException("not found"));

    assertThrows(RuntimeException.class,
      () -> cancellationReasonService.find(CANCELLATION_REASON_ID));
  }
}

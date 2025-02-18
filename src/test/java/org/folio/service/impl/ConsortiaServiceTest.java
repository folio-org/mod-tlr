package org.folio.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.folio.client.feign.ConsortiaClient;
import org.folio.domain.dto.SharingInstance;
import org.folio.domain.dto.Status;
import org.folio.service.ConsortiumService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsortiaServiceTest {

  private static final String INSTANCE_ID = "864eabc2-df06-4df3-9863-029a1f7eb1d3";

  @Mock
  private ConsortiumService consortiumService;

  @Mock
  private ConsortiaClient consortiaClient;

  @InjectMocks
  private ConsortiaServiceImpl consortiaService;

  @Test
  void shareInstanceThrowsExceptionIfSharingFails() {
    when(consortiumService.getCurrentConsortiumId())
      .thenReturn("central_tenant");

    SharingInstance mockSharingResponse = new SharingInstance()
      .status(Status.ERROR)
      .error("sorry");

    when(consortiaClient.shareInstance(any(String.class), any(SharingInstance.class)))
      .thenReturn(mockSharingResponse);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
      () -> consortiaService.shareInstance(INSTANCE_ID, "target_tenant"));

    assertEquals("Failed to share instance 864eabc2-df06-4df3-9863-029a1f7eb1d3 " +
      "with tenant target_tenant: sorry", exception.getMessage());
  }
}
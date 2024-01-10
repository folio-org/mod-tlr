package org.folio.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.folio.service.OpenRequestsProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenRequestsProcessingControllerTest {

  @Mock
  private OpenRequestsProcessingService service;

  @InjectMocks
  private OpenRequestsProcessingController controller;

  @Test
  void openRequestsProcessingServiceCalledFromController() {
    controller.processOpenRequests();
    verify(service, times(1)).processOpenRequests();
  }

}

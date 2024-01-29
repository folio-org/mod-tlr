package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.Optional;

import org.folio.domain.dto.EcsTlr;
import org.folio.service.EcsTlrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class EcsTlrControllerTest {
  @Mock
  private EcsTlrService requestsService;

  @InjectMocks
  private EcsTlrController requestsController;

  @Test
  void getByIdNotFoundWhenNull() {
    when(requestsService.get(any())).thenReturn(Optional.empty());
    var response = requestsController.getEcsTlrById(any());
    verify(requestsService).get(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(404));
  }

  @Test
  void getById() {
    when(requestsService.get(any())).thenReturn(Optional.of(new EcsTlr()));
    var response = requestsController.getEcsTlrById(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(200));
  }

  @Test
  void ecsTlrShouldSuccessfullyBeCreated() {
    var mockRequest = new EcsTlr();
    when(requestsService.post(any(EcsTlr.class))).thenReturn(mockRequest);

    var response = requestsController.postEcsTlr(new EcsTlr());

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(mockRequest, response.getBody());
  }
}

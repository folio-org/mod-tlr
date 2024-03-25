package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.service.EcsTlrService;
import org.folio.service.TlrSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class EcsTlrControllerTest {
  @Mock
  private EcsTlrService ecsTlrService;
  @Mock
  private TlrSettingsService tlrSettingsService;
  @InjectMocks
  private EcsTlrController ecsTlrController;

  @Test
  void getByIdNotFoundWhenNull() {
    when(ecsTlrService.get(any())).thenReturn(Optional.empty());
    var response = ecsTlrController.getEcsTlrById(any());
    verify(ecsTlrService).get(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(404));
  }

  @Test
  void getById() {
    when(ecsTlrService.get(any())).thenReturn(Optional.of(new EcsTlr()));
    var response = ecsTlrController.getEcsTlrById(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(200));
  }

  @Test
  void ecsTlrShouldSuccessfullyBeCreated() {
    var mockRequest = new EcsTlr();
    when(ecsTlrService.create(any(EcsTlr.class))).thenReturn(mockRequest);

    var response = ecsTlrController.postEcsTlr(new EcsTlr());

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(mockRequest, response.getBody());
  }

  @Test
  void ecsTlrShouldSuccessfullyBeUpdated() {
    var id = UUID.randomUUID();
    var mockRequest = new EcsTlr();
    mockRequest.setId(id.toString());
    when(ecsTlrService.update(any(UUID.class), any(EcsTlr.class))).thenReturn(true);

    var response = ecsTlrController.putEcsTlrById(id, mockRequest);
    assertEquals(NO_CONTENT, response.getStatusCode());
  }

  @Test
  void ecsTlrShouldSuccessfullyBeDeleted() {
    when(ecsTlrService.delete(any(UUID.class))).thenReturn(true);
    assertEquals(NO_CONTENT, ecsTlrController.deleteEcsTlrById(UUID.randomUUID()).getStatusCode());
  }

  @Test
  void ecsTlrShouldNotBeFound() {
    var id = UUID.randomUUID();
    var mockRequest = new EcsTlr();
    mockRequest.setId(UUID.randomUUID().toString());

    when(ecsTlrService.update(any(UUID.class), any(EcsTlr.class))).thenReturn(false);
    var putResponse = ecsTlrController.putEcsTlrById(id, mockRequest);
    assertEquals(NOT_FOUND, putResponse.getStatusCode());

    when(ecsTlrService.delete(any(UUID.class))).thenReturn(false);
    var deleteResponse = ecsTlrController.deleteEcsTlrById(id);
    assertEquals(NOT_FOUND, deleteResponse.getStatusCode());
  }
}

package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.util.Optional;

import org.folio.domain.dto.TitleLevelRequest;
import org.folio.service.TitleLevelRequestsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class TitleLevelRequestsControllerTest {
  @Mock
  private TitleLevelRequestsService requestsService;

  @InjectMocks
  private TitleLevelRequestsController requestsController;

  @Test
  void getByIdNotFoundWhenNull() {
    when(requestsService.get(any())).thenReturn(Optional.empty());
    var response = requestsController.getTitleLevelRequestById(any());
    verify(requestsService).get(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(404));
  }

  @Test
  void getById() {
    when(requestsService.get(any())).thenReturn(Optional.of(new TitleLevelRequest()));
    var response = requestsController.getTitleLevelRequestById(any());
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(200));
  }

  @Test
  public void titleLevelRequestShouldSuccessfullyBeCreated() {
    TitleLevelRequest mockRequest = new TitleLevelRequest();
    when(requestsService.post(any(TitleLevelRequest.class))).thenReturn(mockRequest);

   var response = requestsController.postTitleLevelRequest(new TitleLevelRequest());

    assertEquals(CREATED, response.getStatusCode());
    assertEquals(mockRequest, response.getBody());
  }

  @Test
  public void titleLevelRequestShouldReturnError() {
    when(requestsService.post(any(TitleLevelRequest.class))).thenThrow(new NullPointerException());

    assertEquals(INTERNAL_SERVER_ERROR, requestsController.postTitleLevelRequest(
      new TitleLevelRequest()).getStatusCode());
  }
}

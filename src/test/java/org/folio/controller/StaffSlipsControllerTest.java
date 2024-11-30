package org.folio.controller;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.OK;

import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.PickSlipsResponse;
import org.folio.domain.dto.StaffSlip;
import org.folio.service.impl.PickSlipsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class StaffSlipsControllerTest {

  private static final UUID SERVICE_POINT_ID = UUID.fromString("6582fb37-9748-40a0-a0be-51efd151fa53");

  @Mock
  private PickSlipsService pickSlipsService;

  @InjectMocks
  private StaffSlipsController controller;

  @Test
  void pickSlipsAreBuiltSuccessfully() {
    when(pickSlipsService.getStaffSlips(SERVICE_POINT_ID.toString()))
      .thenReturn(List.of(new StaffSlip()));

    ResponseEntity<PickSlipsResponse> response = controller.getPickSlips(SERVICE_POINT_ID);
    assertThat(response.getStatusCode(), is(OK));
    assertThat(response.getBody(), notNullValue());
    assertThat(response.getBody().getTotalRecords(), is(1));
    assertThat(response.getBody().getPickSlips(), hasSize(1));
  }

  @Test
  void noPickSlipsAreBuilt() {
    when(pickSlipsService.getStaffSlips(SERVICE_POINT_ID.toString()))
      .thenReturn(emptyList());

    ResponseEntity<PickSlipsResponse> response = controller.getPickSlips(SERVICE_POINT_ID);
    assertThat(response.getStatusCode(), is(OK));
    assertThat(response.getBody(), notNullValue());
    assertThat(response.getBody().getTotalRecords(), is(0));
    assertThat(response.getBody().getPickSlips(), hasSize(0));
  }
}
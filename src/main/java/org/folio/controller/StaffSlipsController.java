package org.folio.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.folio.domain.dto.PickSlipsResponse;
import org.folio.domain.dto.StaffSlip;
import org.folio.rest.resource.PickSlipsApi;
import org.folio.service.impl.PickSlipsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class StaffSlipsController implements PickSlipsApi {

  private final PickSlipsService pickSlipsService;

  @Override
  public ResponseEntity<PickSlipsResponse> getPickSlips(UUID servicePointId) {
    log.info("getPickSlips:: servicePointId={}", servicePointId);
    Collection<StaffSlip> pickSlips = pickSlipsService.getStaffSlips(servicePointId.toString());

    return ResponseEntity.ok(new PickSlipsResponse()
      .pickSlips(new ArrayList<>(pickSlips))
      .totalRecords(pickSlips.size()));
  }
}

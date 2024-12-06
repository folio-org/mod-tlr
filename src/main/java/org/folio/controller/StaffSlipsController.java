package org.folio.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.folio.domain.dto.PickSlipsResponse;
import org.folio.domain.dto.SearchSlipsResponse;
import org.folio.domain.dto.StaffSlip;
import org.folio.rest.resource.StaffSlipsApi;
import org.folio.service.impl.PickSlipsService;
import org.folio.service.impl.SearchSlipsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
@RequestMapping("/tlr/staff-slips")
public class StaffSlipsController implements StaffSlipsApi {

  private final PickSlipsService pickSlipsService;
  private final SearchSlipsService searchSlipsService;

  @Override
  public ResponseEntity<PickSlipsResponse> getPickSlips(UUID servicePointId) {
    log.info("getPickSlips:: servicePointId={}", servicePointId);
    Collection<StaffSlip> pickSlips = pickSlipsService.getStaffSlips(servicePointId.toString());

    return ResponseEntity.ok(new PickSlipsResponse()
      .pickSlips(new ArrayList<>(pickSlips))
      .totalRecords(pickSlips.size()));
  }

  @Override
  public ResponseEntity<SearchSlipsResponse> getSearchSlips(UUID servicePointId) {
    log.info("getSearchSlips:: servicePointId={}", servicePointId);
    Collection<StaffSlip> searchSlips = searchSlipsService.getStaffSlips(servicePointId.toString());

    return ResponseEntity.ok(new SearchSlipsResponse()
      .searchSlips(new ArrayList<>(searchSlips))
      .totalRecords(searchSlips.size()));
  }
}

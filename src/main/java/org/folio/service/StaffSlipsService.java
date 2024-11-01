package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.StaffSlip;

public interface StaffSlipsService {
  Collection<StaffSlip> getStaffSlips(String servicePointId);
}

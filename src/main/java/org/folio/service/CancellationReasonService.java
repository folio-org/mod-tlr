package org.folio.service;

import org.folio.domain.dto.CancellationReason;

public interface CancellationReasonService {
  CancellationReason find(String id);
}

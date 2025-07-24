package org.folio.service;

import org.folio.domain.dto.DeclareItemLostRequest;

public interface DeclareItemLostService {
  void declareItemLost(DeclareItemLostRequest request);
}

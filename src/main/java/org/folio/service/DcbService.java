package org.folio.service;

import org.folio.domain.entity.EcsTlrEntity;

public interface DcbService {
  void createTransactions(EcsTlrEntity ecsTlr);
}

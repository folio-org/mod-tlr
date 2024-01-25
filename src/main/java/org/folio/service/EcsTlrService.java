package org.folio.service;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;

public interface EcsTlrService {
  Optional<EcsTlr> get(UUID requestId);
  EcsTlr post(EcsTlr ecsTlr);
}

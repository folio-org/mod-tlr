package org.folio.service;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.entity.EcsTlrSettingsEntity;

public interface EcsTlrService {
  Optional<EcsTlr> get(UUID requestId);
  EcsTlr create(EcsTlr ecsTlr);
  boolean update(UUID requestId, EcsTlr ecsTlr);
  boolean delete(UUID requestId);
  Optional<EcsTlrSettings> getEcsTlrSettings();
//  EcsTlrSettings createEcsTlrSettings(EcsTlrSettings ecsTlrSettings);
  Optional<EcsTlrSettingsEntity> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings);
}

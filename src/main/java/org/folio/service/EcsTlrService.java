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
  void updateRequestItem(UUID secondaryRequestId, UUID itemId);
  Optional<EcsTlrSettings> getEcsTlrSettings();
  Optional<EcsTlrSettingsEntity> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings);
}

package org.folio.service;

import java.util.Optional;

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.entity.EcsTlrSettingsEntity;

public interface EcsTlrSettingsService {
  Optional<EcsTlrSettings> getEcsTlrSettings();
  Optional<EcsTlrSettingsEntity> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings);
}

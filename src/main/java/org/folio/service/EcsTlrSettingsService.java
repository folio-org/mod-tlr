package org.folio.service;

import java.util.Optional;

import org.folio.domain.dto.EcsTlrSettings;

public interface EcsTlrSettingsService {
  Optional<EcsTlrSettings> getEcsTlrSettings();
  Optional<EcsTlrSettings> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings);
}

package org.folio.service;

import java.util.Optional;

import org.folio.domain.dto.TlrSettings;

public interface TlrSettingsService {
  Optional<TlrSettings> getTlrSettings();
  Optional<TlrSettings> updateTlrSettings(TlrSettings tlrSettings);
}

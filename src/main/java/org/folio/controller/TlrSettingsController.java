package org.folio.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import org.folio.domain.dto.TlrSettings;
import org.folio.rest.resource.TlrSettingsApi;
import org.folio.service.TlrSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class TlrSettingsController implements TlrSettingsApi {

  private final TlrSettingsService tlrSettingsService;

  @Override
  public ResponseEntity<TlrSettings> getTlrSettings() {
    log.debug("getTlrSettings:: ");

    return tlrSettingsService.getTlrSettings()
      .map(ResponseEntity.status(OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> putTlrSettings(TlrSettings tlrSettings) {
    log.debug("putTlrSettings:: parameters: {}", () -> tlrSettings);

    return ResponseEntity.status(
        tlrSettingsService.updateTlrSettings(tlrSettings)
          .map(entity -> NO_CONTENT)
          .orElse(NOT_FOUND))
      .build();
  }
}

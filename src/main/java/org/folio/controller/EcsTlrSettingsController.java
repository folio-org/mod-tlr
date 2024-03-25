package org.folio.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.rest.resource.EcsTlrSettingsApi;
import org.folio.service.EcsTlrSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@AllArgsConstructor
public class EcsTlrSettingsController implements EcsTlrSettingsApi {

  private final EcsTlrSettingsService ecsTlrSettingsService;

  @Override
  public ResponseEntity<EcsTlrSettings> getEcsTlrSettings() {
    log.debug("getEcsTlrSettings:: ");

    return ecsTlrSettingsService.getEcsTlrSettings()
      .map(ResponseEntity.status(OK)::body)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> putEcsTlrSettings(EcsTlrSettings ecsTlrSettings) {
    log.debug("putEcsTlrSettings:: parameters: {}", () -> ecsTlrSettings);

    return ResponseEntity.status(
        ecsTlrSettingsService.updateEcsTlrSettings(ecsTlrSettings)
          .map(entity -> NO_CONTENT)
          .orElse(NOT_FOUND))
      .build();
  }
}

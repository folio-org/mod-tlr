package org.folio.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.util.Optional;

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.service.EcsTlrSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class EcsTlrSettingsControllerTest {
  @Mock
  private EcsTlrSettingsService ecsTlrSettingsService;
  @InjectMocks
  private EcsTlrSettingsController ecsTlrSettingsController;

  @Test
  void getSettingsNotFoundWhenNull() {
    when(ecsTlrSettingsService.getEcsTlrSettings()).thenReturn(Optional.empty());
    var response = ecsTlrSettingsController.getEcsTlrSettings();
    verify(ecsTlrSettingsService, times(1)).getEcsTlrSettings();
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(404));
  }

  @Test
  void getSettings() {
    when(ecsTlrSettingsService.getEcsTlrSettings()).thenReturn(Optional.of(new EcsTlrSettings()));
    var response = ecsTlrSettingsController.getEcsTlrSettings();
    verify(ecsTlrSettingsService, times(1)).getEcsTlrSettings();
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(200));
  }

  @Test
  void ecsTlrSettingsShouldSuccessfullyBeUpdated() {
    when(ecsTlrSettingsService.updateEcsTlrSettings(any(EcsTlrSettings.class)))
      .thenReturn(Optional.of(new EcsTlrSettings()));

    var response = ecsTlrSettingsController.putEcsTlrSettings(new EcsTlrSettings());
    assertEquals(NO_CONTENT, response.getStatusCode());
  }

  @Test
  void ecsTlrSettingsShouldNotBeUpdated() {
    when(ecsTlrSettingsService.updateEcsTlrSettings(any(EcsTlrSettings.class)))
      .thenReturn(Optional.empty());

    var response = ecsTlrSettingsController.putEcsTlrSettings(new EcsTlrSettings());
    assertEquals(NOT_FOUND, response.getStatusCode());
  }
}

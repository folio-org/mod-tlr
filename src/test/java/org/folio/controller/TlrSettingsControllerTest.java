package org.folio.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.util.Optional;

import org.folio.domain.dto.TlrSettings;
import org.folio.service.TlrSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class TlrSettingsControllerTest {
  @Mock
  private TlrSettingsService tlrSettingsService;
  @InjectMocks
  private TlrSettingsController tlrSettingsController;

  @Test
  void getSettingsNotFoundWhenNull() {
    when(tlrSettingsService.getTlrSettings()).thenReturn(Optional.empty());
    var response = tlrSettingsController.getTlrSettings();
    verify(tlrSettingsService, times(1)).getTlrSettings();
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(404));
  }

  @Test
  void getSettings() {
    when(tlrSettingsService.getTlrSettings()).thenReturn(Optional.of(new TlrSettings()));
    var response = tlrSettingsController.getTlrSettings();
    verify(tlrSettingsService, times(1)).getTlrSettings();
    assertEquals(response.getStatusCode(), HttpStatusCode.valueOf(200));
  }

  @Test
  void tlrSettingsShouldSuccessfullyBeUpdated() {
    when(tlrSettingsService.updateTlrSettings(any(TlrSettings.class)))
      .thenReturn(Optional.of(new TlrSettings()));

    var response = tlrSettingsController.putTlrSettings(new TlrSettings());
    assertEquals(NO_CONTENT, response.getStatusCode());
  }

  @Test
  void tlrSettingsShouldNotBeUpdated() {
    when(tlrSettingsService.updateTlrSettings(any(TlrSettings.class)))
      .thenReturn(Optional.empty());

    var response = tlrSettingsController.putTlrSettings(new TlrSettings());
    assertEquals(NOT_FOUND, response.getStatusCode());
  }
}

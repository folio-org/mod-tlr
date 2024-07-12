package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.TlrSettings;
import org.folio.domain.entity.TlrSettingsEntity;
import org.folio.domain.mapper.TlrSettingsMapper;
import org.folio.domain.mapper.TlrSettingsMapperImpl;
import org.folio.repository.TlrSettingsRepository;
import org.folio.service.impl.TlrSettingsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TlrSettingsServiceTest {
  @Mock
  private TlrSettingsRepository tlrSettingsRepository;
  @Spy
  private final TlrSettingsMapper tlrSettingsMapper = new TlrSettingsMapperImpl();
  @Mock
  private PublishCoordinatorService<TlrSettings> publishCoordinatorService;
  @InjectMocks
  private TlrSettingsServiceImpl tlrSettingsService;

  @Test
  void getTlrSettings() {
    when(tlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(new TlrSettingsEntity(UUID.randomUUID(), true))));

    Optional<TlrSettings> tlrSettings = tlrSettingsService.getTlrSettings();
    verify(tlrSettingsRepository).findAll(any(PageRequest.class));
    assertTrue(tlrSettings.isPresent());
    assertTrue(tlrSettings.map(TlrSettings::getEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void getTlrSettingsWithEmptyValue() {
    when(tlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<TlrSettings> tlrSettings = tlrSettingsService.getTlrSettings();
    verify(tlrSettingsRepository).findAll(any(PageRequest.class));
    assertFalse(tlrSettings.isPresent());
  }

  @Test
  void updateTlrSettings() {
    var tlrSettingsEntity = new TlrSettingsEntity(UUID.randomUUID(), true);
    when(tlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(tlrSettingsEntity)));
    when(tlrSettingsRepository.save(any(TlrSettingsEntity.class)))
      .thenReturn(tlrSettingsEntity);

    TlrSettings tlrSettings = new TlrSettings();
    tlrSettings.ecsTlrFeatureEnabled(true);
    Optional<TlrSettings> tlrSettingsResponse = tlrSettingsService.updateTlrSettings(tlrSettings);
    verify(tlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(tlrSettingsRepository, times(1)).save(any(TlrSettingsEntity.class));
    verify(publishCoordinatorService, times(1)).updateForAllTenants(any(TlrSettings.class));
    assertTrue(tlrSettingsResponse.isPresent());
    assertTrue(tlrSettingsResponse.map(TlrSettings::getEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void cannotUpdateTlrSettingsIfItDoesNotExist() {
    when(tlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<TlrSettings> tlrSettings = tlrSettingsService.updateTlrSettings(new TlrSettings());
    verify(tlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(tlrSettingsRepository, times(0)).save(any(TlrSettingsEntity.class));
    assertFalse(tlrSettings.isPresent());
  }
}

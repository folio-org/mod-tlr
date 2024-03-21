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

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.entity.EcsTlrSettingsEntity;
import org.folio.domain.mapper.EcsTlrSettingsMapper;
import org.folio.domain.mapper.EcsTlrSettingsMapperImpl;
import org.folio.repository.EcsTlrSettingsRepository;
import org.folio.service.impl.EcsTlrSettingsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class EcsTlrSettingsServiceTest {

  @InjectMocks
  private EcsTlrSettingsServiceImpl ecsTlrSettingsService;
  @Mock
  private EcsTlrSettingsRepository ecsTlrSettingsRepository;
  @Spy
  private final EcsTlrSettingsMapper ecsTlrSettingsMapper = new EcsTlrSettingsMapperImpl();

  @Test
  void getEcsTlrSettings() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(new EcsTlrSettingsEntity(UUID.randomUUID(), true))));

    Optional<EcsTlrSettings> ecsTlrSettings = ecsTlrSettingsService.getEcsTlrSettings();
    verify(ecsTlrSettingsRepository).findAll(any(PageRequest.class));
    assertTrue(ecsTlrSettings.isPresent());
    assertTrue(ecsTlrSettings.map(EcsTlrSettings::getEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void getEcsTlrSettingsWithEmptyValue() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<EcsTlrSettings> ecsTlrSettings = ecsTlrSettingsService.getEcsTlrSettings();
    verify(ecsTlrSettingsRepository).findAll(any(PageRequest.class));
    assertFalse(ecsTlrSettings.isPresent());
  }

  @Test
  void updateEcsTlrSettings() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(new EcsTlrSettingsEntity(UUID.randomUUID(), true))));

    Optional<EcsTlrSettingsEntity> ecsTlrSettings = ecsTlrSettingsService.updateEcsTlrSettings(new EcsTlrSettings());
    verify(ecsTlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(ecsTlrSettingsRepository, times(1)).save(any(EcsTlrSettingsEntity.class));
    assertTrue(ecsTlrSettings.isPresent());
    assertTrue(ecsTlrSettings.map(EcsTlrSettingsEntity::isEcsTlrFeatureEnabled).orElse(false));
  }

  @Test
  void cannotUpdateEcsTlrSettingsIfItDoesNotExist() {
    when(ecsTlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(Collections.emptyList()));

    Optional<EcsTlrSettingsEntity> ecsTlrSettings = ecsTlrSettingsService.updateEcsTlrSettings(new EcsTlrSettings());
    verify(ecsTlrSettingsRepository, times(1)).findAll(any(PageRequest.class));
    verify(ecsTlrSettingsRepository, times(0)).save(any(EcsTlrSettingsEntity.class));
    assertFalse(ecsTlrSettings.isPresent());
  }
}

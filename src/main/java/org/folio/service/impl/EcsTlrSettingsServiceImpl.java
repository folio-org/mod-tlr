package org.folio.service.impl;

import java.util.Optional;

import org.folio.domain.dto.EcsTlrSettings;
import org.folio.domain.entity.EcsTlrSettingsEntity;
import org.folio.domain.mapper.EcsTlrSettingsMapper;
import org.folio.repository.EcsTlrSettingsRepository;
import org.folio.service.EcsTlrSettingsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcsTlrSettingsServiceImpl implements EcsTlrSettingsService {

  private final EcsTlrSettingsRepository ecsTlrSettingsRepository;
  private final EcsTlrSettingsMapper ecsTlrSettingsMapper;

  @Override
  public Optional<EcsTlrSettings> getEcsTlrSettings() {
    log.debug("getEcsTlrSettings:: ");

    return ecsTlrSettingsRepository.findAll(PageRequest.of(0, 1))
      .stream()
      .findFirst()
      .map(ecsTlrSettingsMapper::mapEntityToDto);
  }

  @Override
  public Optional<EcsTlrSettingsEntity> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings) {
    log.debug("updateEcsTlrSettings:: parameters: {} ", () -> ecsTlrSettings);

    Optional<EcsTlrSettingsEntity> existentSettings = ecsTlrSettingsRepository.findAll(PageRequest.of(0, 1))
      .stream()
      .findFirst();

    existentSettings.ifPresent(entity -> {
      ecsTlrSettings.setId(entity.getId().toString());
      ecsTlrSettingsRepository.save(ecsTlrSettingsMapper.mapDtoToEntity(ecsTlrSettings));
    });

    return existentSettings;
  }
}

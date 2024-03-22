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
  public Optional<EcsTlrSettings> updateEcsTlrSettings(EcsTlrSettings ecsTlrSettings) {
    log.debug("updateEcsTlrSettings:: parameters: {} ", () -> ecsTlrSettings);

    return ecsTlrSettingsRepository.findAll(PageRequest.of(0, 1))
      .stream()
      .findFirst()
      .map(entity -> {
        EcsTlrSettingsEntity save = ecsTlrSettingsRepository.save(ecsTlrSettingsMapper.mapDtoToEntity(
          ecsTlrSettings.id(entity.getId().toString())));
        return ecsTlrSettingsMapper.mapEntityToDto(save);
      });
  }
}

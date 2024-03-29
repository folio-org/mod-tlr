package org.folio.service.impl;

import java.util.Optional;

import org.folio.domain.dto.TlrSettings;
import org.folio.domain.mapper.TlrSettingsMapper;
import org.folio.repository.TlrSettingsRepository;
import org.folio.service.TlrSettingsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class TlrSettingsServiceImpl implements TlrSettingsService {

  private final TlrSettingsRepository tlrSettingsRepository;
  private final TlrSettingsMapper tlrSettingsMapper;

  @Override
  public Optional<TlrSettings> getTlrSettings() {
    log.debug("getTlrSettings:: ");

    return tlrSettingsRepository.findAll(PageRequest.of(0, 1))
      .stream()
      .findFirst()
      .map(tlrSettingsMapper::mapEntityToDto);
  }

  @Override
  public Optional<TlrSettings> updateTlrSettings(TlrSettings tlrSettings) {
    log.debug("updateTlrSettings:: parameters: {} ", () -> tlrSettings);

    return tlrSettingsRepository.findAll(PageRequest.of(0, 1))
      .stream()
      .findFirst()
      .map(entity -> tlrSettingsMapper.mapEntityToDto(
        tlrSettingsRepository.save(tlrSettingsMapper.mapDtoToEntity(
          tlrSettings.id(entity.getId().toString())))));
  }
}

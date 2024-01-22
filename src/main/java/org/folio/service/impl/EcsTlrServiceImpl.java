package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.domain.dto.EcsTlr;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.EcsTlrService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcsTlrServiceImpl implements EcsTlrService {

  private final EcsTlrRepository ecsTlrRepository;
  private final EcsTlrMapper requestsMapper;

  @Override
  public Optional<EcsTlr> get(UUID id) {
    log.debug("get:: parameters id: {}", id);

    return ecsTlrRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }

  @Override
  public EcsTlr post(EcsTlr ecsTlr) {
    log.debug("post:: parameters ecsTlr: {}", () -> ecsTlr);

    return requestsMapper.mapEntityToDto(ecsTlrRepository.save(
      requestsMapper.mapDtoToEntity(ecsTlr)));
  }

  @Override
  public boolean put(UUID requestId, EcsTlr ecsTlr) {
    log.debug("put:: requestId: {}, ecsTlr: {}", requestId, ecsTlr);

    return ecsTlrRepository.findById(requestId)
      .map(ecsTlrEntity -> requestsMapper.mapDtoToEntity(ecsTlr))
      .map(ecsTlrRepository::save)
      .isPresent();
  }

  @Override
  public boolean delete(UUID requestId) {
    return ecsTlrRepository.findById(requestId)
        .map(ecsTlrEntity -> {
          ecsTlrRepository.deleteById(ecsTlrEntity.getId());
          return ecsTlrEntity;
        })
      .isPresent();
  }
}

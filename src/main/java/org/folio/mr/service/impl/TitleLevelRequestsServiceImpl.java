package org.folio.mr.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.mr.domain.dto.TitleLevelRequest;
import org.folio.mr.domain.mapper.TitleLevelRequestMapper;
import org.folio.mr.repository.TitleLevelRequestsRepository;
import org.folio.mr.service.TitleLevelRequestsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class TitleLevelRequestsServiceImpl implements TitleLevelRequestsService {

  private final TitleLevelRequestsRepository titleLevelRequestsRepository;
  private final TitleLevelRequestMapper requestsMapper;

  @Override
  public Optional<TitleLevelRequest> get(UUID id) {
    return titleLevelRequestsRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }
}

package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.CirculationClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.TenantScopedExecutionService;
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
  private final CirculationClient circulationClient;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  @Override
  public Optional<EcsTlr> get(UUID id) {
    log.debug("get:: parameters id: {}", id);

    return ecsTlrRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }

  @Override
  public EcsTlr create(EcsTlr ecsTlr) {
    log.debug("create:: parameters ecsTlr: {}", () -> ecsTlr);
    createRequest(ecsTlr, "dummy-tenant"); // TODO: replace with real tenantId

    return requestsMapper.mapEntityToDto(ecsTlrRepository.save(
      requestsMapper.mapDtoToEntity(ecsTlr)));
  }

  private Request createRequest(EcsTlr ecsTlr, String tenantId) {
    log.info("createRequest:: creating request for ECS TLR {} and tenant {}", ecsTlr.getId(), tenantId);
    Request mappedRequest = requestsMapper.mapDtoToRequest(ecsTlr);
    Request createdRequest = tenantScopedExecutionService.execute(tenantId,
      () -> circulationClient.createTitleLevelRequest(mappedRequest));
    log.info("createRequest:: request created: {}", createdRequest.getId());
    log.debug("createRequest:: request={}", () -> createdRequest);

    return createdRequest;
  }
}

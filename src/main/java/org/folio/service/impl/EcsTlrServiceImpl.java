package org.folio.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.CirculationClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
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
  public EcsTlr post(EcsTlr ecsTlr) {
    log.debug("post:: parameters ecsTlr: {}", () -> ecsTlr);
    createRemoteRequest(ecsTlr, "university"); // TODO: replace with real tenantId

    return requestsMapper.mapEntityToDto(ecsTlrRepository.save(
      requestsMapper.mapDtoToEntity(ecsTlr)));
  }

  @Override
  public void updateRequestItem(UUID tlrRequestId, UUID itemId) {
    log.debug("updateRequestItem:: parameters tlrRequestId: {}, itemId: {}", tlrRequestId, itemId);
    ecsTlrRepository.findById(tlrRequestId).ifPresentOrElse(
      ecsTlrEntity -> {
        ecsTlrEntity.setItemId(itemId);
        ecsTlrRepository.save(ecsTlrEntity);
      },
      () -> log.error("updateRequestItem:: EcsTlr with tlrId: {} not found", tlrRequestId));
  }

  private Request createRemoteRequest(EcsTlr ecsTlr, String tenantId) {
    log.info("createRemoteRequest:: creating remote request for ECS TLR {} and tenant {}", ecsTlr.getId(), tenantId);
    Request mappedRequest = requestsMapper.mapDtoToRequest(ecsTlr);
    Request createdRequest = tenantScopedExecutionService.execute(tenantId,
      () -> circulationClient.createRequest(mappedRequest));
    log.info("createRemoteRequest:: request created: {}", createdRequest.getId());
    log.debug("createRemoteRequest:: request: {}", () -> createdRequest);

    return createdRequest;
  }
}

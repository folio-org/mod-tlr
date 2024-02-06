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
    createRemoteRequest(ecsTlr, "university"); // TODO: replace with real tenantId

    return requestsMapper.mapEntityToDto(ecsTlrRepository.save(
      requestsMapper.mapDtoToEntity(ecsTlr)));
  }

  @Override
  public boolean update(UUID requestId, EcsTlr ecsTlr) {
    log.debug("update:: parameters requestId: {}, ecsTlr: {}", () -> requestId, () -> ecsTlr);

    if (ecsTlrRepository.existsById(requestId)) {
      ecsTlrRepository.save(requestsMapper.mapDtoToEntity(ecsTlr));
      return true;
    }
    return false;
  }

  @Override
  public boolean delete(UUID requestId) {
    log.debug("delete:: parameters requestId: {}", () -> requestId);

    if (ecsTlrRepository.existsById(requestId)) {
      ecsTlrRepository.deleteById(requestId);
      return true;
    }
    return false;
  }

  @Override
  public void updateRequestItem(UUID secondaryRequestId, UUID itemId) {
    log.debug("updateRequestItem:: parameters secondaryRequestId: {}, itemId: {}", secondaryRequestId, itemId);
    ecsTlrRepository.findBySecondaryTlrId(secondaryRequestId).ifPresentOrElse(
      ecsTlr -> {
        if (!itemId.equals(ecsTlr.getItemId())) {
          ecsTlr.setItemId(itemId);
          ecsTlrRepository.save(ecsTlr);
        } else {
          log.info("updateRequestItem: ECS TLR with secondary request ID: {} is already updated", secondaryRequestId);
        }
      },
      () -> log.error("updateRequestItem: ECS TLR with secondary request ID: {} not found", secondaryRequestId));
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

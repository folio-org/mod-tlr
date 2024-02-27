package org.folio.service.impl;

import static java.lang.String.format;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.client.feign.CirculationClient;
import org.folio.domain.dto.EcsTlr;
import org.folio.domain.dto.Request;
import org.folio.domain.mapper.EcsTlrMapper;
import org.folio.domain.strategy.TenantPickingStrategy;
import org.folio.exception.RequestCreatingException;
import org.folio.exception.TenantPickingException;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.EcsTlrService;
import org.folio.service.TenantScopedExecutionService;
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
  private final TenantPickingStrategy tenantPickingStrategy;

  @Override
  public Optional<EcsTlr> get(UUID id) {
    log.debug("get:: parameters id: {}", id);

    return ecsTlrRepository.findById(id)
      .map(requestsMapper::mapEntityToDto);
  }

  @Override
  public EcsTlr create(EcsTlr ecsTlr) {
    log.debug("create:: parameters ecsTlr: {}", () -> ecsTlr);
    final String instanceId = ecsTlr.getInstanceId();

    List<String> tenantIds = tenantPickingStrategy.findTenants(instanceId);
    if (tenantIds.isEmpty()) {
      log.error("create:: failed to find tenants for instance: {}", instanceId);
      throw new TenantPickingException("Failed to find tenants for instance " + instanceId);
    }
    for (String tenantId : tenantIds) {
      try {
        return createRequest(ecsTlr, tenantId);
      } catch (Exception e) {
        log.error("create:: failed create a request for tenant {}: {}", tenantId, e.getMessage());
        log.debug("create:: ", e);
      }
    }
    throw new RequestCreatingException(format(
      "Failed to create a request for instanceId: %s in tenants: %s", instanceId, tenantIds));
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

  private EcsTlr createRequest(EcsTlr ecsTlr, String tenantId) {
    log.info("createRequest:: creating request for ECS TLR {} in tenant {}", ecsTlr.getId(), tenantId);

    Request mappedRequest = requestsMapper.mapDtoToRequest(ecsTlr);
    Request createdRequest = tenantScopedExecutionService.execute(tenantId,
      () -> circulationClient.createInstanceRequest(mappedRequest));

    log.info("createRequest:: request created: {}", createdRequest.getId());
    log.debug("createRequest:: request: {}", () -> createdRequest);

    ecsTlr.secondaryRequestTenantId(tenantId)
      .secondaryRequestId(createdRequest.getId())
      .itemId(createdRequest.getItemId());

    log.debug("createRequest:: updating ECS TLR: {}", () -> ecsTlr);

    return requestsMapper.mapEntityToDto(ecsTlrRepository.save(
      requestsMapper.mapDtoToEntity(ecsTlr)));
  }
}

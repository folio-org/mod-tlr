package org.folio.service.impl;

import java.util.function.Function;

import org.folio.service.ReplicationService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public abstract class ReplicationServiceImpl<T> implements ReplicationService<T> {

  @Autowired
  private SystemUserScopedExecutionService executor;
  private final Function<T, String> idExtractor;

  public T replicate(T original, String targetTenant) {
    final String id = idExtractor.apply(original);
    final String type = original.getClass().getSimpleName();
    log.info("replicate:: looking for {} {} in tenant {}", type, id, targetTenant);
    T replica;
    try {
      replica = executor.executeSystemUserScoped(targetTenant, () -> find(id));
      log.info("replicate:: {} {} already exists in tenant {}", type, id, targetTenant);
    } catch (FeignException.NotFound e) {
      log.info("replicate:: {} {} not found in tenant {}, creating it", type, id, targetTenant);
      replica = executor.executeSystemUserScoped(targetTenant, () -> create(buildReplica(original)));
      log.info("replicate:: {} {} created in tenant {}", type, id, targetTenant);
    }
    return replica;
  }

  protected abstract T find(String objectId);

  protected abstract T create(T replica);

  protected abstract T buildReplica(T original);
}

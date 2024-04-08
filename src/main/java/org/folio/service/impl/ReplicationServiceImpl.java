package org.folio.service.impl;

import java.util.function.Function;

import org.folio.service.ReplicationService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public abstract class ReplicationServiceImpl<T> implements ReplicationService<T> {

  private final Function<T, String> idExtractor;

  public T replicate(T original) {
    final String id = idExtractor.apply(original);
    final String type = original.getClass().getSimpleName();
    log.info("replicate:: looking for {} {} ", type, id);
    T replica;
    try {
      replica = find(id);
      log.info("replicate:: {} {} already exists", type, id);
    } catch (FeignException.NotFound e) {
      log.info("replicate:: {} {} not found, creating it", type, id);
      replica = create(buildReplica(original));
      log.info("replicate:: {} {} created", type, id);
    }
    return replica;
  }

  protected abstract T find(String objectId);

  protected abstract T create(T replica);

  protected abstract T buildReplica(T original);
}

package org.folio.service.impl;

import java.util.function.Function;

import org.folio.service.CloningService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public abstract class CloningServiceImpl<T> implements CloningService<T> {

  private final Function<T, String> idExtractor;

  public T clone(T original) {
    final String id = idExtractor.apply(original);
    final String type = original.getClass().getSimpleName();
    log.info("clone:: looking for {} {} ", type, id);
    T clone;
    try {
      clone = find(id);
      log.info("clone:: {} {} already exists", type, id);
    } catch (FeignException.NotFound e) {
      log.info("clone:: {} {} not found, creating it", type, id);
      clone = create(buildClone(original));
      log.info("clone:: {} {} created", type, id);
    }
    return clone;
  }

  protected abstract T find(String objectId);

  protected abstract T create(T clone);

  protected abstract T buildClone(T original);
}

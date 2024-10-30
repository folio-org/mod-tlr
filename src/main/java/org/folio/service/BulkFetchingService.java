package org.folio.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.folio.client.feign.GetByQueryClient;
import org.folio.support.CqlQuery;

public interface BulkFetchingService {

  <C, E> Collection<E> fetch(GetByQueryClient<C> client, Collection<String> ids,
    Function<C, Collection<E>> collectionExtractor);

  <C, E> Map<String, E> fetch(GetByQueryClient<C> client, Collection<String> ids,
    Function<C, Collection<E>> collectionExtractor, Function<E, String> keyMapper);

  <C, E> Collection<E> fetch(GetByQueryClient<C> client, CqlQuery query, String idIndex,
    Collection<String> ids, Function<C, Collection<E>> collectionExtractor);
}

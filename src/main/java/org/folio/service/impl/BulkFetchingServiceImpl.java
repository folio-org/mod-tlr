package org.folio.service.impl;

import static java.util.Collections.emptyList;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.folio.client.feign.GetByQueryClient;
import org.folio.service.BulkFetchingService;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Service
@Log4j2
public class BulkFetchingServiceImpl implements BulkFetchingService {
  private static final int MAX_IDS_PER_QUERY = 70;

  @Override
  public <C, E> Collection<E> fetch(GetByQueryClient<C> client, Collection<String> ids,
    Function<C, Collection<E>> collectionExtractor) {

    return fetch(buildQueries(ids), client, collectionExtractor);
  }

  @Override
  public <C, E> Map<String, E> fetch(GetByQueryClient<C> client, Collection<String> ids,
    Function<C, Collection<E>> collectionExtractor, Function<E, String> keyMapper) {

    return fetch(client, ids, collectionExtractor)
      .stream()
      .collect(toMap(keyMapper, identity()));
  }

  @Override
  public <C, E> Collection<E> fetch(GetByQueryClient<C> client, CqlQuery commonQuery, String idIndex,
    Collection<String> ids, Function<C, Collection<E>> collectionExtractor) {

    return fetch(buildQueries(commonQuery, idIndex, ids), client, collectionExtractor);
  }


  private <C, E> List<E> fetch(Collection<CqlQuery> queries, GetByQueryClient<C> client,
    Function<C, Collection<E>> collectionExtractor) {

    if (queries.isEmpty()) {
      log.info("getAsStream:: provided collection of queries is empty, fetching nothing");
      return emptyList();
    }

    List<E> result = queries.stream()
      .map(client::getByQuery)
      .map(collectionExtractor)
      .flatMap(Collection::stream)
      .toList();

    log.info("fetch:: fetched {} objects", result::size);
    return result;
  }

  private static Collection<CqlQuery> buildQueries(Collection<String> ids) {
    return buildQueries(CqlQuery.empty(), "id", ids);
  }

  private static Collection<CqlQuery> buildQueries(CqlQuery commonQuery, String index, Collection<String> ids) {
    List<String> uniqueIds = ids.stream()
      .peek(UUID::fromString)
      .distinct()
      .toList();

    log.info("buildQueries:: building queries: commonQuery={}, index={}, ids={}" ,
      commonQuery, index, uniqueIds.size());
    log.debug("buildQueries:: ids={}", uniqueIds);

    List<CqlQuery> queries = Lists.partition(uniqueIds, MAX_IDS_PER_QUERY)
      .stream()
      .map(batch -> CqlQuery.exactMatchAny(index, batch))
      .map(commonQuery::and)
      .toList();

    log.info("buildQueries:: built {} queries", queries::size);
    log.debug("buildQueries:: queries={}", queries);

    return queries;
  }

  @PostConstruct
  private void postConstruct() {
    log.info("postConstruct:: MAX_IDS_PER_QUERY={}", MAX_IDS_PER_QUERY);
  }

}

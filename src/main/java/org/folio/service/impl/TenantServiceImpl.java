package org.folio.service.impl;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.base.Predicates.notNull;
import static java.util.Comparator.comparingLong;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.domain.dto.ItemStatusEnum.AVAILABLE;
import static org.folio.domain.dto.ItemStatusEnum.CHECKED_OUT;
import static org.folio.domain.dto.ItemStatusEnum.IN_TRANSIT;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.folio.client.feign.SearchInstanceClient;
import org.folio.domain.dto.ItemStatusEnum;
import org.folio.domain.dto.SearchHolding;
import org.folio.domain.dto.SearchInstance;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.dto.SearchItem;
import org.folio.domain.dto.SearchItemStatus;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.ConsortiumService;
import org.folio.service.TenantService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@RequiredArgsConstructor
@Log4j2
public class TenantServiceImpl implements TenantService {
  private final SearchInstanceClient searchClient;
  private final ConsortiumService consortiumService;

  @Override
  public String getPrimaryRequestTenantId(EcsTlrEntity ecsTlr) {
    log.info("getPrimaryRequestTenantId:: getting borrowing tenant");
    if (ecsTlr == null || ecsTlr.getPrimaryRequestTenantId() == null) {
      log.info("getPrimaryRequestTenantId:: central tenant by default");
      return consortiumService.getCentralTenantId();
    }

    log.info("getPrimaryRequestTenantId:: returning primaryRequestTenantId");
    return ecsTlr.getPrimaryRequestTenantId();
  }

  @Override
  public List<String> getSecondaryRequestTenants(EcsTlrEntity ecsTlr) {
    final String instanceId = ecsTlr.getInstanceId().toString();
    log.info("getSecondaryRequestTenants:: looking for potential secondary request tenants " +
      "for instance {}", instanceId);
    var searchInstanceResponse = searchClient.searchInstance(instanceId);
    var itemStatusOccurrencesByTenant = getItemStatusOccurrencesByTenant(searchInstanceResponse);

    List<String> tenantIds;
    if (itemStatusOccurrencesByTenant.isEmpty()) {
      log.info("getSecondaryRequestTenants:: no items found, looking for tenants with holdings");

      tenantIds = getHoldingOccurrencesByTenant(searchInstanceResponse)
        .entrySet()
        .stream()
        .sorted(comparingLong(Entry::getValue))
        .map(Entry::getKey)
        .toList();
    } else {
      log.info("getSecondaryRequestTenants:: item status occurrences by tenant: {}",
        itemStatusOccurrencesByTenant);

      tenantIds = itemStatusOccurrencesByTenant.entrySet()
        .stream()
        .sorted(compareByItemCount(AVAILABLE)
          .thenComparing(compareByItemCount(CHECKED_OUT, IN_TRANSIT))
          .thenComparing(compareByItemCount(alwaysTrue())))
        .map(Entry::getKey)
        .toList();
    }

    if (tenantIds.isEmpty()) {
      log.warn("getSecondaryRequestTenants:: failed to find secondary request tenants for instance {}", instanceId);
    } else {
      log.info("getSecondaryRequestTenants:: found tenants for instance {}: {}", instanceId, tenantIds);
    }

    return tenantIds;
  }

  private Map<String, Map<String, Long>> getItemStatusOccurrencesByTenant(
    SearchInstancesResponse searchInstancesResponse) {

    return searchInstancesResponse
      .getInstances()
      .stream()
      .filter(Objects::nonNull)
      .map(SearchInstance::getItems)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .filter(item -> item.getTenantId() != null)
      .collect(collectingAndThen(groupingBy(SearchItem::getTenantId),
        TenantServiceImpl::mapItemsToItemStatusOccurrences));
  }

  private Map<String, Long> getHoldingOccurrencesByTenant(
    SearchInstancesResponse searchInstancesResponse) {

    return searchInstancesResponse
      .getInstances()
      .stream()
      .filter(Objects::nonNull)
      .map(SearchInstance::getHoldings)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .map(SearchHolding::getTenantId)
      .filter(Objects::nonNull)
      .collect(groupingBy(identity(), counting()));
  }

  @NotNull
  private static Map<String, Map<String, Long>> mapItemsToItemStatusOccurrences(
    Map<String, List<SearchItem>> itemsByTenant) {

    return itemsByTenant.entrySet()
      .stream()
      .collect(toMap(Entry::getKey, entry -> entry.getValue()
        .stream()
        .distinct()
        .map(SearchItem::getStatus)
        .filter(notNull())
        .map(SearchItemStatus::getName)
        .filter(notNull())
        .collect(groupingBy(identity(), counting()))
      ));
  }

  private static Comparator<Entry<String, Map<String, Long>>> compareByItemCount(
    ItemStatusEnum... desiredStatuses) {

    Set<String> statusStrings = Arrays.stream(desiredStatuses)
      .map(ItemStatusEnum::getValue)
      .collect(toSet());

    return compareByItemCount(statusStrings::contains);
  }

  private static Comparator<Entry<String, Map<String, Long>>> compareByItemCount(
    Predicate<String> statusFilter) {

    return comparingLong((Entry<String, Map<String, Long>> entry) -> entry.getValue()
      .entrySet()
      .stream()
      .filter(e -> statusFilter.test(e.getKey()))
      .map(Entry::getValue)
      .reduce(0L, Long::sum))
      .reversed();
  }

}

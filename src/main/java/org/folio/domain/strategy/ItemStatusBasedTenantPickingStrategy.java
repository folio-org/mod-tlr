package org.folio.domain.strategy;

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
import java.util.Set;
import java.util.function.Predicate;

import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.ItemStatusEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@RequiredArgsConstructor
@Log4j2
public class ItemStatusBasedTenantPickingStrategy implements TenantPickingStrategy {
  private final SearchClient searchClient;

  @Override
  public List<String> findTenants(String instanceId) {
    log.info("findTenants:: find tenants for a TLR for instance {}", instanceId);

    var itemStatusOccurrencesByTenant = getItemStatusOccurrencesByTenant(instanceId);
    log.info("findTenants:: item status occurrences by tenant: {}", itemStatusOccurrencesByTenant);

    List<String> sortedTenantIds = itemStatusOccurrencesByTenant.entrySet()
      .stream()
      .sorted(compareByItemCount(AVAILABLE)
        .thenComparing(compareByItemCount(CHECKED_OUT, IN_TRANSIT))
        .thenComparing(compareByItemCount(alwaysTrue())))
      .map(Entry::getKey)
      .toList();

    if (sortedTenantIds.isEmpty()) {
      log.warn("findTenants:: failed to find tenants for instance {}", instanceId);
    } else {
      log.info("findTenants:: tenants for instance {} found: {}", instanceId, sortedTenantIds);
    }

    return sortedTenantIds;
  }

  private Map<String, Map<String, Long>> getItemStatusOccurrencesByTenant(String instanceId) {
    return searchClient.searchInstance(instanceId)
      .getInstances()
      .stream()
      .filter(notNull())
      .map(Instance::getItems)
      .flatMap(Collection::stream)
      .filter(item -> item.getTenantId() != null)
      .collect(collectingAndThen(groupingBy(Item::getTenantId),
        ItemStatusBasedTenantPickingStrategy::mapItemsToItemStatusOccurrences));
  }

  @NotNull
  private static Map<String, Map<String, Long>> mapItemsToItemStatusOccurrences(
    Map<String, List<Item>> itemsByTenant) {

    return itemsByTenant.entrySet()
      .stream()
      .collect(toMap(Entry::getKey, entry -> entry.getValue()
        .stream()
        .distinct()
        .map(Item::getStatus)
        .filter(notNull())
        .map(ItemStatus::getName)
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

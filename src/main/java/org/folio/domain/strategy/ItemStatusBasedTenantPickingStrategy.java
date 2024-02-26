package org.folio.domain.strategy;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.base.Predicates.notNull;
import static java.util.Comparator.comparingLong;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
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
  public Set<String> pickTenants(String instanceId) {
    log.info("pickTenant:: picking tenant for a TLR for instance {}", instanceId);

    var itemStatusOccurrencesByTenant = getItemStatusOccurrencesByTenant(instanceId);
    log.info("pickTenant:: item status occurrences by tenant: {}", itemStatusOccurrencesByTenant);

    Set<String> sortedTenantIds = itemStatusOccurrencesByTenant.entrySet()
      .stream()
      .sorted(
        comparingLong(
          (Map.Entry<String, Map<String, Long>> entry) -> countItems(entry, Set.of("Available")::contains))
        .thenComparingLong(
          (Map.Entry<String, Map<String, Long>> entry) -> countItems(entry,
            Set.of("Checked out", "In transit")::contains))
        .thenComparingLong((Map.Entry<String, Map<String, Long>> entry) -> countItems(
          entry, alwaysTrue())))
      .map(Entry::getKey)
      .collect(Collectors.toCollection(LinkedHashSet::new));

    if (sortedTenantIds.isEmpty()) {
      log.warn("pickTenant:: failed to pick tenant for instance {}", instanceId);
    } else {
      log.info("pickTenant:: tenant for instance {} found: {}", instanceId, sortedTenantIds);
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

  private static long countItems(Map.Entry<String, Map<String, Long>> itemStatuses,
    Predicate<String> statusPredicate) {

    return itemStatuses.getValue()
      .entrySet()
      .stream()
      .filter(entry -> statusPredicate.test(entry.getKey()))
      .map(Map.Entry::getValue)
      .reduce(0L, Long::sum);
  }

}

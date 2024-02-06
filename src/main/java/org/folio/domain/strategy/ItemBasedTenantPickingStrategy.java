package org.folio.domain.strategy;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.base.Predicates.notNull;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

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
public class ItemBasedTenantPickingStrategy implements TenantPickingStrategy {
  private static final String STATUS_AVAILABLE = "Available";
  private static final String STATUS_CHECKED_OUT = "Checked out";
  private static final String STATUS_IN_TRANSIT = "In transit";

  private final SearchClient searchClient;

  @Override
  public Optional<String> pickTenant(String instanceId) {
    log.info("pickTenant:: picking tenant for a TLR for instance {}", instanceId);

    Map<String, Map<String, Long>> statusOccurrencesByTenant = searchClient.searchInstance(instanceId)
      .getInstances()
      .stream()
      .filter(instance -> instance.getTenantId() != null)
      .collect(collectingAndThen(groupingBy(Instance::getTenantId),
        ItemBasedTenantPickingStrategy::mapInstancesToItemStatusOccurrences));

    log.info("pickTenant:: item status occurrences by tenant: {}", statusOccurrencesByTenant);

    Optional<String> tenantId = Optional.<String>empty()
      .or(() -> pickTenant(statusOccurrencesByTenant, Set.of(STATUS_AVAILABLE)))
      .or(() -> pickTenant(statusOccurrencesByTenant, Set.of(STATUS_CHECKED_OUT, STATUS_IN_TRANSIT)))
      .or(() -> pickTenant(statusOccurrencesByTenant, alwaysTrue())); // any status

    tenantId.ifPresentOrElse(
      id -> log.info("pickTenant:: tenant for instance {} found: {}", instanceId, id),
      () -> log.warn("pickTenant:: failed to pick tenant for instance {}", instanceId));

    return tenantId;
  }

  @NotNull
  private static Map<String, Map<String, Long>> mapInstancesToItemStatusOccurrences(
    Map<String, List<Instance>> instancesByTenant) {

    return instancesByTenant.entrySet()
      .stream()
      .collect(toMap(Entry::getKey, entry -> entry.getValue()
        .stream()
        .map(Instance::getItems)
        .flatMap(Collection::stream)
        .distinct()
        .map(Item::getStatus)
        .filter(notNull())
        .map(ItemStatus::getName)
        .filter(notNull())
        .collect(groupingBy(identity(), counting()))
      ));
  }

  private static Optional<String> pickTenant(Map<String, Map<String, Long>> statusOccurrencesByTenant,
    Set<String> desiredStatuses) {

    log.info("pickTenant:: looking for tenant with most items in statuses {}", desiredStatuses);
    return pickTenant(statusOccurrencesByTenant, desiredStatuses::contains);
  }

  private static Optional<String> pickTenant(Map<String, Map<String, Long>> statusOccurrencesByTenant,
    Predicate<String> statusFilter) {

    return statusOccurrencesByTenant.entrySet()
      .stream()
      .collect(toMap(Entry::getKey, entry -> entry.getValue()
        .entrySet()
        .stream()
        .filter(subMapEntry -> statusFilter.test(subMapEntry.getKey()))
        .map(Entry::getValue)
        .reduce(0L, Long::sum)
      ))
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue() > 0)
      .max(comparingByValue())
      .map(Entry::getKey);
  }

}

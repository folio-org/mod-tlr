package org.folio.service.impl;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.base.Predicates.notNull;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.folio.domain.dto.ItemStatusEnum.AVAILABLE;
import static org.folio.domain.dto.ItemStatusEnum.CHECKED_OUT;
import static org.folio.domain.dto.ItemStatusEnum.IN_TRANSIT;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.ItemStatusEnum;
import org.folio.service.TenantService;
import org.folio.util.HttpUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@RequiredArgsConstructor
@Log4j2
public class TenantServiceImpl implements TenantService {
  private final SearchClient searchClient;

  @Override
  public Optional<String> getBorrowingTenant() {
    return HttpUtils.getTenantFromToken();
  }

  @Override
  public Optional<String> getLendingTenant(String instanceId) {
    log.info("pickTenant:: picking lending tenant for a TLR for instance {}", instanceId);

    var itemStatusOccurrencesByTenant = getItemStatusOccurrencesByTenant(instanceId);
    log.info("pickTenant:: item status occurrences by tenant: {}", itemStatusOccurrencesByTenant);

    Optional<String> tenantId = Optional.<String>empty()
      .or(() -> pickLendingTenant(itemStatusOccurrencesByTenant, EnumSet.of(AVAILABLE)))
      .or(() -> pickLendingTenant(itemStatusOccurrencesByTenant, EnumSet.of(CHECKED_OUT, IN_TRANSIT)))
      .or(() -> pickLendingTenant(itemStatusOccurrencesByTenant, alwaysTrue())); // any status

    tenantId.ifPresentOrElse(
      id -> log.info("pickTenant:: lending tenant for instance {} found: {}", instanceId, id),
      () -> log.warn("pickTenant:: failed to pick lending tenant for instance {}", instanceId));

    return tenantId;
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
        TenantServiceImpl::mapItemsToItemStatusOccurrences));
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

  private static Optional<String> pickLendingTenant(Map<String, Map<String, Long>> statusOccurrencesByTenant,
    EnumSet<ItemStatusEnum> desiredStatuses) {

    List<String> desiredStatusValues = desiredStatuses.stream()
      .map(ItemStatusEnum::getValue)
      .toList();

    log.info("pickTenant:: looking for tenant with most items in statuses {}", desiredStatuses);
    return pickLendingTenant(statusOccurrencesByTenant, desiredStatusValues::contains);
  }

  private static Optional<String> pickLendingTenant(Map<String, Map<String, Long>> statusOccurrencesByTenant,
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

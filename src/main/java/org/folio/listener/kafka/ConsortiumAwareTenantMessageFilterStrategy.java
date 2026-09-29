package org.folio.listener.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.folio.service.ConsortiumService;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.kafka.filtering.entitlement.TenantEntitlementService;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class ConsortiumAwareTenantMessageFilterStrategy<K, V>
  implements RecordFilterStrategy<K, V> {

  private static final String FOLIO_TENANT_ID_HEADER = "folio.tenantId";

  private final RecordFilterStrategy<K, V> entitlementFilter;
  private final TenantEntitlementService tenantEntitlementService;
  private final ConsortiumService consortiumService;
  private final FolioModuleMetadata folioModuleMetadata;

  @Override
  public boolean filter(ConsumerRecord<K, V> record) {
    String eventTenantId = resolveTenant(record);
    if (eventTenantId == null) {
      return entitlementFilter.filter(record);
    }

    Set<String> enabledTenants = tenantEntitlementService.getEnabledTenants();
    if (enabledTenants == null || enabledTenants.isEmpty() || enabledTenants.contains(eventTenantId)) {
      return entitlementFilter.filter(record);
    }

    String centralTenantId = resolveCentralTenant(record, eventTenantId);
    if (centralTenantId == null || centralTenantId.equals(eventTenantId)) {
      return entitlementFilter.filter(record);
    }

    log.debug("Filtering consortium event against central tenant entitlement: "
        + "messageKey = {}, eventTenant = {}, centralTenant = {}",
      record.key(), eventTenantId, centralTenantId);

    return entitlementFilter.filter(copyWithTenant(record, centralTenantId));
  }

  @Override
  public boolean ignoreEmptyBatch() {
    return entitlementFilter.ignoreEmptyBatch();
  }

  private String resolveCentralTenant(ConsumerRecord<K, V> record, String eventTenantId) {
    Map<String, Object> messageHeaders = new HashMap<>();
    record.headers().forEach(header -> messageHeaders.put(header.key(), header.value()));
    messageHeaders.put(XOkapiHeaders.TENANT, eventTenantId.getBytes(UTF_8));

    var context = DefaultFolioExecutionContext.fromMessageHeaders(folioModuleMetadata, messageHeaders);
    try (var ignored = new FolioExecutionContextSetter(context)) {
      return trimToNull(consortiumService.getCentralTenantId());
    }
  }

  private static String resolveTenant(ConsumerRecord<?, ?> record) {
    String tenantId = findHeaderValue(record, XOkapiHeaders.TENANT);
    return tenantId != null ? tenantId : findHeaderValue(record, FOLIO_TENANT_ID_HEADER);
  }

  private static String findHeaderValue(ConsumerRecord<?, ?> record, String headerName) {
    for (Header header : record.headers()) {
      if (headerName.equalsIgnoreCase(header.key()) && header.value() != null) {
        String value = trimToNull(new String(header.value(), UTF_8));
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  private static <K, V> ConsumerRecord<K, V> copyWithTenant(
    ConsumerRecord<K, V> record, String tenantId) {

    var headers = new RecordHeaders();
    record.headers().forEach(header -> {
      if (!isTenantHeader(header.key())) {
        headers.add(header);
      }
    });
    headers.add(XOkapiHeaders.TENANT, tenantId.getBytes(UTF_8));

    return new ConsumerRecord<>(
      record.topic(),
      record.partition(),
      record.offset(),
      record.timestamp(),
      record.timestampType(),
      record.serializedKeySize(),
      record.serializedValueSize(),
      record.key(),
      record.value(),
      headers,
      record.leaderEpoch(),
      record.deliveryCount());
  }

  private static boolean isTenantHeader(String headerName) {
    return XOkapiHeaders.TENANT.equalsIgnoreCase(headerName)
      || FOLIO_TENANT_ID_HEADER.equalsIgnoreCase(headerName);
  }
}

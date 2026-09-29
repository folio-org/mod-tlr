package org.folio.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.util.Locale;
import java.util.Optional;

import org.folio.listener.kafka.ConsortiumAwareTenantMessageFilterStrategy;
import org.folio.service.ConsortiumService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.kafka.filtering.configuration.KafkaTenantFilterProperties;
import org.folio.spring.kafka.filtering.entitlement.TenantEntitlementService;
import org.folio.spring.kafka.filtering.filter.EnabledTenantMessageFilterStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
  prefix = "folio.kafka.tenant-filter",
  name = "enabled",
  havingValue = "true"
)
public class KafkaTenantFilterConfiguration {

  public static final String TENANT_FILTER_BEAN = "tenantAwareMessageFilter";

  @Bean
  public FolioModuleMetadata folioModuleMetadata(
    @Value("${spring.application.name}") String applicationName,
    @Value("${spring.application.version}") String applicationVersion) {

    String schemaSuffix = isNotBlank(applicationName)
      ? "_" + applicationName.toLowerCase(Locale.ROOT).replace('-', '_')
      : "";

    return new FolioModuleMetadata() {

      @Override
      public String getModuleName() {
        return applicationName;
      }

      @Override
      public Optional<String> getModuleVersion() {
        return Optional.ofNullable(trimToNull(applicationVersion));
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        if (isBlank(tenantId)) {
          throw new IllegalArgumentException("tenantId can't be null or empty");
        }
        return tenantId.toLowerCase(Locale.ROOT) + schemaSuffix;
      }
    };
  }

  @Bean(name = TENANT_FILTER_BEAN)
  public <K, V> RecordFilterStrategy<K, V> consortiumAwareTenantMessageFilter(
    TenantEntitlementService tenantEntitlementService,
    KafkaTenantFilterProperties properties,
    ConsortiumService consortiumService,
    FolioModuleMetadata folioModuleMetadata) {

    var entitlementFilter = new EnabledTenantMessageFilterStrategy<K, V>(
      tenantEntitlementService.getModuleId(),
      tenantEntitlementService,
      properties.isIgnoreEmptyBatch(),
      properties.getTenantDisabledStrategy(),
      properties.getAllTenantsDisabledStrategy());

    return new ConsortiumAwareTenantMessageFilterStrategy<>(
      entitlementFilter, tenantEntitlementService, consortiumService, folioModuleMetadata);
  }
}

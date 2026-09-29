package org.folio.listener;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.kafka.filtering.filter.DisabledTenantStrategy.FAIL;
import static org.folio.spring.kafka.filtering.filter.DisabledTenantStrategy.SKIP;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.listener.kafka.ConsortiumAwareTenantMessageFilterStrategy;
import org.folio.service.ConsortiumService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.kafka.filtering.entitlement.TenantEntitlementService;
import org.folio.spring.kafka.filtering.filter.EnabledTenantMessageFilterStrategy;
import org.folio.spring.kafka.filtering.filter.TenantsAreDisabledException;
import org.folio.spring.scope.EmptyFolioExecutionContextHolder;
import org.folio.spring.scope.FolioExecutionScopeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsortiumAwareTenantMessageFilterStrategyTest {

  private static final String MODULE_ID = "mod-tlr-1.3.0";
  private static final String MEMBER_TENANT = "member";
  private static final String CENTRAL_TENANT = "central";

  private TenantEntitlementService tenantEntitlementService;
  private final AtomicReference<Set<String>> enabledTenants = new AtomicReference<>();
  private TestConsortiumService consortiumService;
  private ConsortiumAwareTenantMessageFilterStrategy<String, String> filter;

  @BeforeEach
  void setUp() {
    var moduleMetadata = new TestModuleMetadata();
    consortiumService = new TestConsortiumService(moduleMetadata);
    tenantEntitlementService = new TenantEntitlementService(MODULE_ID, ignored -> enabledTenants.get());
    var entitlementFilter = new EnabledTenantMessageFilterStrategy<String, String>(
      MODULE_ID, tenantEntitlementService, true, SKIP, FAIL);
    filter = new ConsortiumAwareTenantMessageFilterStrategy<>(
      entitlementFilter, tenantEntitlementService, consortiumService, moduleMetadata);
  }

  @Test
  void shouldAcceptMemberEventWhenCentralTenantIsEnabled() {
    enabledTenants.set(Set.of(CENTRAL_TENANT));
    consortiumService.centralTenantId = CENTRAL_TENANT;
    ConsumerRecord<String, String> record = recordWithHeader(TENANT, MEMBER_TENANT);

    assertThat(filter.filter(record)).isFalse();
    assertThat(headerValue(record, TENANT)).isEqualTo(MEMBER_TENANT);
    assertThat(consortiumService.lookupCount).isOne();
    assertThat(consortiumService.observedTenantId).isEqualTo(MEMBER_TENANT);
  }

  @Test
  void shouldFilterMemberEventWhenCentralTenantIsNotEnabled() {
    enabledTenants.set(Set.of("another-central"));
    consortiumService.centralTenantId = CENTRAL_TENANT;

    assertThat(filter.filter(recordWithHeader(TENANT, MEMBER_TENANT))).isTrue();
  }

  @Test
  void shouldUseFolioTenantIdHeaderAsFallback() {
    enabledTenants.set(Set.of(CENTRAL_TENANT));
    consortiumService.centralTenantId = CENTRAL_TENANT;

    assertThat(filter.filter(recordWithHeader("folio.tenantId", MEMBER_TENANT))).isFalse();
  }

  @Test
  void shouldNotResolveConsortiumForDirectlyEnabledTenant() {
    enabledTenants.set(Set.of(CENTRAL_TENANT));

    assertThat(filter.filter(recordWithHeader(TENANT, CENTRAL_TENANT))).isFalse();
    assertThat(consortiumService.lookupCount).isZero();
  }

  @Test
  void shouldApplyAllTenantsDisabledStrategyBeforeConsortiumLookup() {
    enabledTenants.set(Set.of());

    assertThatThrownBy(() -> filter.filter(recordWithHeader(TENANT, MEMBER_TENANT)))
      .isInstanceOf(TenantsAreDisabledException.class);
    assertThat(consortiumService.lookupCount).isZero();
  }

  @Test
  void shouldPropagateConsortiumLookupFailure() {
    enabledTenants.set(Set.of(CENTRAL_TENANT));
    consortiumService.failure = new IllegalStateException("lookup failed");

    assertThatThrownBy(() -> filter.filter(recordWithHeader(TENANT, MEMBER_TENANT)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("lookup failed");
  }

  @Test
  void shouldPreserveDelegateBatchBehavior() {
    assertThat(filter.ignoreEmptyBatch()).isTrue();
  }

  private static ConsumerRecord<String, String> recordWithHeader(String name, String value) {
    var record = new ConsumerRecord<String, String>("topic", 0, 1, "key", "value");
    record.headers().add(name, value.getBytes(UTF_8));
    return record;
  }

  private static String headerValue(ConsumerRecord<?, ?> record, String name) {
    return new String(record.headers().lastHeader(name).value(), UTF_8);
  }

  private static final class TestModuleMetadata implements FolioModuleMetadata {

    @Override
    public String getModuleName() {
      return "mod-tlr";
    }

    @Override
    public String getDBSchemaName(String tenantId) {
      return tenantId + "_mod_tlr";
    }
  }

  private static final class TestConsortiumService implements ConsortiumService {

    private final FolioExecutionScopeConfig executionScopeConfig;
    private String centralTenantId;
    private RuntimeException failure;
    private int lookupCount;
    private String observedTenantId;

    private TestConsortiumService(FolioModuleMetadata moduleMetadata) {
      executionScopeConfig = new FolioExecutionScopeConfig(
        new EmptyFolioExecutionContextHolder(moduleMetadata));
    }

    @Override
    public String getCurrentTenantId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentConsortiumId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCentralTenantId() {
      lookupCount++;
      observedTenantId = executionScopeConfig.folioExecutionContext().getTenantId();
      if (failure != null) {
        throw failure;
      }
      return centralTenantId;
    }

    @Override
    public boolean isCurrentTenantCentral() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCentralTenant(String tenantId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrentTenantConsortiumMember() {
      throw new UnsupportedOperationException();
    }
  }
}

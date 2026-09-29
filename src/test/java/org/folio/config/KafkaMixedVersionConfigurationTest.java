package org.folio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.folio.listener.kafka.ConsortiumAwareTenantMessageFilterStrategy;
import org.folio.listener.kafka.KafkaEventListener;
import org.folio.service.ConsortiumService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.kafka.filtering.configuration.KafkaConsumerFilteringConfiguration;
import org.folio.spring.kafka.filtering.configuration.KafkaTenantFilterProperties;
import org.folio.spring.kafka.filtering.entitlement.TenantEntitlementService;
import org.folio.spring.kafka.filtering.filter.DisabledTenantStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

class KafkaMixedVersionConfigurationTest {

  private static final String TENANT_FILTER_BEAN = "tenantAwareMessageFilter";

  @Test
  void shouldBuildVersionSpecificConsumerGroupFromModuleVersionOverride() {
    try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
      .web(WebApplicationType.NONE)
      .logStartupInfo(false)
      .run("--ENV=test", "--MODULE_VERSION=1.1.0", "--spring.main.banner-mode=off")) {

      assertThat(context.getEnvironment().getProperty("spring.application.version"))
        .isEqualTo("1.1.0");
      assertThat(context.getEnvironment().getProperty("spring.kafka.consumer.group-id"))
        .isEqualTo("test-mod-tlr-group-1.1.0");
    }
  }

  @Test
  void shouldProvideNoOpTenantFilterWhenFilteringIsDisabled() {
    new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(KafkaConsumerFilteringConfiguration.class))
      .run(context -> {
        assertThat(context).hasSingleBean(RecordFilterStrategy.class);
        assertThat(context).hasBean(TENANT_FILTER_BEAN);
      });
  }

  @Test
  void shouldProvideConsortiumAwareFilterWhenFilteringIsEnabled() {
    new ApplicationContextRunner()
      .withUserConfiguration(KafkaTenantFilterConfiguration.class)
      .withPropertyValues(
        "folio.kafka.tenant-filter.enabled=true",
        "spring.application.name=mod-tlr",
        "spring.application.version=1.1.0")
      .withBean(TenantEntitlementService.class,
        () -> new TenantEntitlementService("mod-tlr-1.1.0", ignored -> Set.of()))
      .withBean(KafkaTenantFilterProperties.class, KafkaMixedVersionConfigurationTest::filterProperties)
      .withBean(ConsortiumService.class, TestConsortiumService::new)
      .run(context -> {
        assertThat(context.getBean(TENANT_FILTER_BEAN))
          .isInstanceOf(ConsortiumAwareTenantMessageFilterStrategy.class);
        assertThat(context.getBean(FolioModuleMetadata.class).getModuleId())
          .isEqualTo("mod-tlr-1.1.0");
      });
  }

  @Test
  void shouldApplyTenantFilterToEveryKafkaListener() {
    Set<Method> listenerMethods = Arrays.stream(KafkaEventListener.class.getDeclaredMethods())
      .filter(method -> method.isAnnotationPresent(KafkaListener.class))
      .collect(Collectors.toSet());

    assertThat(listenerMethods).hasSize(6);
    assertThat(listenerMethods)
      .allSatisfy(method -> assertThat(method.getAnnotation(KafkaListener.class).filter())
        .isEqualTo(TENANT_FILTER_BEAN));
  }

  @Configuration(proxyBeanMethods = false)
  static class EmptyConfiguration {
  }

  private static KafkaTenantFilterProperties filterProperties() {
    var properties = new KafkaTenantFilterProperties();
    properties.setEnabled(true);
    properties.setIgnoreEmptyBatch(true);
    properties.setTenantDisabledStrategy(DisabledTenantStrategy.SKIP);
    properties.setAllTenantsDisabledStrategy(DisabledTenantStrategy.FAIL);
    return properties;
  }

  private static final class TestConsortiumService implements ConsortiumService {

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
      return "central";
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

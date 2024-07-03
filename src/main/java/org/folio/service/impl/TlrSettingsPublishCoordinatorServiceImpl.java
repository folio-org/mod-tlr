package org.folio.service.impl;

import static java.util.Optional.of;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.folio.client.feign.ConsortiaClient;
import org.folio.client.feign.PublishCoordinatorClient;
import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TlrSettings;
import org.folio.domain.dto.UserTenant;
import org.folio.service.PublishCoordinatorService;
import org.folio.service.UserTenantsService;
import org.springframework.stereotype.Service;

import com.bettercloud.vault.json.JsonObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class TlrSettingsPublishCoordinatorServiceImpl implements PublishCoordinatorService<TlrSettings> {
  private static final String CIRCULATION_SETTINGS_URL = "/circulation/settings";
  private final UserTenantsService userTenantsService;
  private final PublishCoordinatorClient publishCoordinatorClient;
  private final ConsortiaClient consortiaClient;

  @Override
  public Optional<TlrSettings> updateForAllTenants(TlrSettings tlrSettings) {
    log.debug("updateForAllTenants:: parameters: {} ", () -> tlrSettings);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    if (firstUserTenant != null) {
      log.info("updateForAllTenants:: firstUserTenant: {}", () -> firstUserTenant);
      Set<String> tenantIds = consortiaClient.getConsortiaTenants(firstUserTenant.getConsortiumId())
        .getTenants()
        .stream()
        .filter(tenant -> !tenant.getIsCentral())
        .map(Tenant::getId)
        .collect(Collectors.toSet());
      log.info("updateForAllTenants:: tenantIds: {}", () -> tenantIds);
      PublicationResponse publicationResponse = publishCoordinatorClient.publish(
        mapTlrSettingsToPublicationRequest(tlrSettings, tenantIds));
      log.info("updateForAllTenants:: publicationResponse: {}", () -> publicationResponse);
    }

    return of(tlrSettings);
  }

  private PublicationRequest mapTlrSettingsToPublicationRequest(TlrSettings tlrSettings,
    Set<String> tenantIds) {

    return new PublicationRequest()
      .url(CIRCULATION_SETTINGS_URL)
      .method("POST")
      .tenants(tenantIds)
      .payload(new JsonObject()
        .add("name", "ecsTlrFeature")
        .add("value", new JsonObject().add("enabled", tlrSettings.getEcsTlrFeatureEnabled())));
  }
}

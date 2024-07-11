package org.folio.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.folio.client.feign.ConsortiaClient;
import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TlrSettings;
import org.folio.domain.dto.UserTenant;
import org.folio.service.PublishCoordinatorService;
import org.folio.service.UserTenantsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class TlrSettingsPublishCoordinatorServiceImpl implements PublishCoordinatorService<TlrSettings> {
  private static final String CIRCULATION_SETTINGS_URL = "/circulation/settings";
  private static final String POST_METHOD = "POST";
  private static final String ECS_TLR_FEATURE = "ecsTlrFeature";
  private final UserTenantsService userTenantsService;
  private final ConsortiaClient consortiaClient;

  @Override
  public PublicationResponse updateForAllTenants(TlrSettings tlrSettings) {
    log.debug("updateForAllTenants:: parameters: {} ", () -> tlrSettings);
    UserTenant firstUserTenant = userTenantsService.findFirstUserTenant();
    PublicationResponse publicationResponse = null;
    if (firstUserTenant != null) {
      String consortiumId = firstUserTenant.getConsortiumId();
      log.info("updateForAllTenants:: firstUserTenant: {}", () -> firstUserTenant);
      Set<String> tenantIds = consortiaClient.getConsortiaTenants(consortiumId)
        .getTenants()
        .stream()
        .filter(tenant -> !tenant.getIsCentral())
        .map(Tenant::getId)
        .collect(Collectors.toSet());
      log.info("updateForAllTenants:: tenantIds: {}", () -> tenantIds);
      publicationResponse = consortiaClient.postPublications(consortiumId,
        mapTlrSettingsToPublicationRequest(tlrSettings, tenantIds));
      log.info("updateForAllTenants:: publicationResponse id: {}, status: {}",
        publicationResponse.getId(), publicationResponse.getStatus());
    } else {
      log.error("updateForAllTenants:: userTenant was not found");
    }

    return publicationResponse;
  }

  private PublicationRequest mapTlrSettingsToPublicationRequest(TlrSettings tlrSettings,
    Set<String> tenantIds) {

    Map<String, Object> payload = new HashMap<>();
    payload.put("name", ECS_TLR_FEATURE);
    payload.put("value", Collections.singletonMap("enabled", tlrSettings.getEcsTlrFeatureEnabled()));
    PublicationRequest publicationRequest = new PublicationRequest()
      .url(CIRCULATION_SETTINGS_URL)
      .method(POST_METHOD)
      .tenants(tenantIds)
      .payload(payload);
    log.info("mapTlrSettingsToPublicationRequest:: result: url: {}," +
      "method: {}, tenants: {}, payload: {}", publicationRequest::getUrl,
      publicationRequest::getMethod, publicationRequest::getTenants,
      publicationRequest::getPayload);

    return publicationRequest;
  }
}

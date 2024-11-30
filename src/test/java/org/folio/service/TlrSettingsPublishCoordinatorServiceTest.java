package org.folio.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.folio.client.feign.ConsortiaClient;
import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.domain.dto.Tenant;
import org.folio.domain.dto.TenantCollection;
import org.folio.domain.dto.TlrSettings;
import org.folio.domain.dto.UserTenant;
import org.folio.service.impl.TlrSettingsPublishCoordinatorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TlrSettingsPublishCoordinatorServiceTest {

  @Mock
  private UserTenantsService userTenantsService;
  @Mock
  private ConsortiaClient consortiaClient;

  @InjectMocks
  TlrSettingsPublishCoordinatorServiceImpl tlrSettingsService;

  @Test
  void updateForAllTenantsShouldNotPublishWhenFirstUserTenantNotFound() {
    when(userTenantsService.findFirstUserTenant()).thenReturn(null);
    tlrSettingsService.updateForAllTenants(new TlrSettings());

    verify(consortiaClient, never()).postPublications(Mockito.anyString(),
      Mockito.any(PublicationRequest.class));
  }

  @Test
  void updateForAllTenantsShouldCallPublish() {
    UserTenant userTenant = new UserTenant();
    userTenant.setConsortiumId("TestConsortiumId");

    TenantCollection tenantCollection = new TenantCollection();
    Tenant tenant = new Tenant();
    tenant.setIsCentral(false);
    tenant.setId("TestTenant");
    tenantCollection.setTenants(Collections.singletonList(tenant));

    when(userTenantsService.findFirstUserTenant()).thenReturn(userTenant);
    when(consortiaClient.getConsortiaTenants(userTenant.getConsortiumId())).thenReturn(tenantCollection);
    when(consortiaClient.postPublications(Mockito.anyString(), Mockito.any(PublicationRequest.class)))
      .thenReturn(new PublicationResponse());
    tlrSettingsService.updateForAllTenants(new TlrSettings().ecsTlrFeatureEnabled(true));

    verify(consortiaClient, times(1)).postPublications(
      Mockito.anyString(), Mockito.any(PublicationRequest.class));
  }
}

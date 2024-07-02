package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.folio.client.feign.UserTenantsClient;
import org.folio.domain.dto.UserTenant;
import org.folio.domain.dto.UserTenantCollection;
import org.folio.service.impl.UserTenantsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTenantsServiceTest {

  @Mock
  private UserTenantsClient userTenantsClient;

  @InjectMocks
  UserTenantsServiceImpl userTenantsService;

  @Test
  void findFirstUserTenantShouldReturnFirstUserTenant() {
    UserTenant userTenant = new UserTenant()
      .id(UUID.randomUUID().toString())
      .tenantId(UUID.randomUUID().toString())
      .centralTenantId(UUID.randomUUID().toString());
    UserTenantCollection userTenantCollection = new UserTenantCollection();
    userTenantCollection.addUserTenantsItem(userTenant);

    when(userTenantsClient.getUserTenants(1)).thenReturn(userTenantCollection);
    assertEquals(userTenant, userTenantsService.findFirstUserTenant());
  }

  @Test
  void findFirstUserTenantShouldReturnNullWhenUserTenantCollectionIsEmpty() {
    UserTenantCollection userTenantCollection = new UserTenantCollection();
    userTenantCollection.setUserTenants(new ArrayList<>());

    when(userTenantsClient.getUserTenants(1)).thenReturn(userTenantCollection);
    assertNull(userTenantsService.findFirstUserTenant());
  }

  @Test
  void findFirstUserTenantShouldReturnNullWhenUserTenantCollectionIsNull() {
    when(userTenantsClient.getUserTenants(1)).thenReturn(null);
    assertNull(userTenantsService.findFirstUserTenant());
  }

}

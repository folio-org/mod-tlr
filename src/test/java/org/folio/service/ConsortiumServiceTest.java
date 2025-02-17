package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.domain.dto.UserTenant;
import org.folio.service.impl.ConsortiumServiceImpl;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsortiumServiceTest {

  private static final String CENTRAL_TENANT_ID = "central_tenant";
  private static final String CONSORTIUM_ID = "5f3febe1-b2a3-45cd-8606-4b160f24575b";
  private static final UserTenant MOCK_USER_TENANT = new UserTenant()
    .centralTenantId(CENTRAL_TENANT_ID)
    .consortiumId(CONSORTIUM_ID);

  @Mock
  private UserTenantsService userTenantsService;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @InjectMocks
  private ConsortiumServiceImpl consortiumService;


  @BeforeEach
  void setUp() {
    ConsortiumServiceImpl.clearCache();
  }

  @Test
  void getCurrentTenantIdReturnsTenantIdFromFolioContext() {
    mockFolioExecutionContext("test_tenant");
    assertEquals("test_tenant", consortiumService.getCurrentTenantId());
  }

  @Test
  void tenantContextIsResolvedAndCached() {
    mockFolioExecutionContext("tenant1");
    mockUserTenant(MOCK_USER_TENANT);

    // first invocation, cache is empty
    assertEquals(CENTRAL_TENANT_ID, consortiumService.getCentralTenantId());
    assertEquals(CONSORTIUM_ID, consortiumService.getCurrentConsortiumId());
    verify(userTenantsService, times(1)).findFirstUserTenant();

    // second invocation, tenant context is resolved from cache
    assertEquals(CENTRAL_TENANT_ID, consortiumService.getCentralTenantId());
    assertEquals(CONSORTIUM_ID, consortiumService.getCurrentConsortiumId());
    verifyNoMoreInteractions(userTenantsService);

    // same steps for a different tenant
    mockFolioExecutionContext("tenant2");
    mockUserTenant(MOCK_USER_TENANT);

    assertEquals(CENTRAL_TENANT_ID, consortiumService.getCentralTenantId());
    assertEquals(CONSORTIUM_ID, consortiumService.getCurrentConsortiumId());
    verify(userTenantsService, times(2)).findFirstUserTenant();

    assertEquals(CENTRAL_TENANT_ID, consortiumService.getCentralTenantId());
    assertEquals(CONSORTIUM_ID, consortiumService.getCurrentConsortiumId());
    verifyNoMoreInteractions(userTenantsService);
  }

  @Test
  void getCentralTenantIdThrowsExceptionIfUserTenantIsNotFound() {
    mockFolioExecutionContext(CENTRAL_TENANT_ID);
    mockUserTenant(null);
    assertThrows(IllegalStateException.class, () -> consortiumService.getCentralTenantId());
  }

  @Test
  void isCurrentTenantCentralReturnsTrue() {
    mockFolioExecutionContext(CENTRAL_TENANT_ID);
    mockUserTenant(MOCK_USER_TENANT);
    assertTrue(consortiumService.isCurrentTenantCentral());
  }

  @Test
  void isCurrentTenantCentralReturnsFalseWhenTenantIdsDoNotMatch() {
    mockFolioExecutionContext("random_tenant");
    mockUserTenant(MOCK_USER_TENANT);
    assertFalse(consortiumService.isCurrentTenantCentral());
  }

  @Test
  void isCurrentTenantCentralThrowsExceptionWhenCentralTenantIdIsNotFound() {
    mockFolioExecutionContext(CENTRAL_TENANT_ID);
    mockUserTenant(null);
    assertThrows(IllegalStateException.class, () -> consortiumService.isCurrentTenantCentral());
  }
  
  @Test
  void isCentralTenantReturnsTrue() {
    mockFolioExecutionContext("random_tenant");
    mockUserTenant(MOCK_USER_TENANT);
    assertTrue(consortiumService.isCentralTenant(CENTRAL_TENANT_ID));
  }

  @Test
  void isCentralTenantReturnsFalseIfTenantIdsDoNotMatch() {
    mockFolioExecutionContext("random_tenant");
    mockUserTenant(MOCK_USER_TENANT);
    assertFalse(consortiumService.isCentralTenant("random_tenant"));
  }

  @Test
  void isCentralTenantThrowsExceptionIfCentralTenantIdIsNotFound() {
    mockFolioExecutionContext("random_tenant");
    mockUserTenant(null);
    assertThrows(IllegalStateException.class, () -> consortiumService.isCentralTenant(CENTRAL_TENANT_ID));
  }

  private void mockUserTenant(UserTenant userTenant) {
    when(userTenantsService.findFirstUserTenant())
      .thenReturn(userTenant);
  }

  private void mockFolioExecutionContext(String tenantId) {
    when(folioExecutionContext.getTenantId())
      .thenReturn(tenantId);
  }

}
package org.folio.service;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.folio.exception.TenantScopedExecutionException;
import org.folio.service.impl.TenantScopedExecutionServiceImpl;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantScopedExecutionServiceTest {

  @Mock
  private FolioExecutionContext folioExecutionContext;
  @InjectMocks
  private TenantScopedExecutionServiceImpl executionService;

  @Test
  void executionExceptionIsForwarded() {
    when(folioExecutionContext.getAllHeaders()).thenReturn(new HashMap<>());
    String tenantId = "test-tenant";
    String errorMessage = "cause message";

    TenantScopedExecutionException exception = assertThrows(TenantScopedExecutionException.class,
      () -> executionService.execute(tenantId, () -> {
        throw new IllegalAccessException(errorMessage);
      }));

    assertEquals(tenantId, exception.getTenantId());
    assertNotNull(exception.getCause());
    assertInstanceOf(IllegalAccessException.class, exception.getCause());
    assertEquals(errorMessage, exception.getCause().getMessage());
  }
}
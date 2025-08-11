package org.folio.service.impl;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckOutServiceImplTest {

  @Test
  void shouldReturnAllHeaders() {
    FolioExecutionContext context = mock(FolioExecutionContext.class);
    String permissionValue = "test-permission";
    String requestIdValue = "request-id-value";
    Map<String, Collection<String>> okapiHeaders = new HashMap<>();
    okapiHeaders.put(XOkapiHeaders.PERMISSIONS, singletonList(permissionValue));
    okapiHeaders.put(XOkapiHeaders.REQUEST_ID, singletonList(requestIdValue));
    when(context.getOkapiHeaders()).thenReturn(okapiHeaders);

    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, context);
    Map<String, String> headers = service.getPermissionsFromContext();

    assertThat(headers.get(XOkapiHeaders.PERMISSIONS), is(permissionValue));
    assertThat(headers.get(XOkapiHeaders.REQUEST_ID), is(requestIdValue));
  }

  @Test
  void shouldReturnEmptyMapIfNoHeaders() {
    FolioExecutionContext context = Mockito.mock(FolioExecutionContext.class);
    when(context.getOkapiHeaders()).thenReturn(new HashMap<>());
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, context);
    Map<String, String> headers = service.getPermissionsFromContext();
    assertThat(headers.isEmpty(), is(true));
  }

  @Test
  void shouldReturnOnlyRelevantHeaders() {
    FolioExecutionContext context = Mockito.mock(FolioExecutionContext.class);
    Map<String, Collection<String>> okapiHeaders = new HashMap<>();
    okapiHeaders.put("X-Okapi-Tenant", singletonList("tenant"));
    okapiHeaders.put(XOkapiHeaders.PERMISSIONS, singletonList("perm"));
    okapiHeaders.put(XOkapiHeaders.REQUEST_ID, singletonList("reqid"));
    when(context.getOkapiHeaders()).thenReturn(okapiHeaders);
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, context);
    Map<String, String> headers = service.getPermissionsFromContext();
    assertThat(headers.size(), is(2));
    assertThat(headers.get(XOkapiHeaders.PERMISSIONS), is("perm"));
    assertThat(headers.get(XOkapiHeaders.REQUEST_ID), is("reqid"));
  }
}

package org.folio.service.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;

class CheckOutServiceImplTest {

  private FolioExecutionContext mockContextWithHeaders(Map<String, Collection<String>> headers) {
    FolioExecutionContext ctx = mock(FolioExecutionContext.class);
    when(ctx.getOkapiHeaders()).thenReturn(headers);
    return ctx;
  }

  @Test
  void shouldReturnAllHeaders() {
    Map<String, Collection<String>> headers = Map.of(
      XOkapiHeaders.PERMISSIONS, List.of("test-permission"),
      XOkapiHeaders.REQUEST_ID, List.of("request-id-value")
    );
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, mockContextWithHeaders(headers));
    Map<String, String> result = service.getHeadersFromContext();
    assertThat(result.get(XOkapiHeaders.PERMISSIONS), is("test-permission"));
    assertThat(result.get(XOkapiHeaders.REQUEST_ID), is("request-id-value"));
  }

  @Test
  void shouldReturnEmptyMapIfNoHeaders() {
    Map<String, Collection<String>> headers = Map.of();
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, mockContextWithHeaders(headers));
    Map<String, String> result = service.getHeadersFromContext();
    assertThat(result.isEmpty(), is(true));
  }

  @Test
  void shouldReturnOnlyRelevantHeaders() {
    Map<String, Collection<String>> headers = Map.of(
      XOkapiHeaders.TENANT, List.of("tenant"),
      XOkapiHeaders.PERMISSIONS, List.of("perm"),
      XOkapiHeaders.REQUEST_ID, List.of("reqid")
    );
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null, mockContextWithHeaders(headers));
    Map<String, String> result = service.getHeadersFromContext();
    assertThat(result.size(), is(2));
    assertThat(result.get(XOkapiHeaders.PERMISSIONS), is("perm"));
    assertThat(result.get(XOkapiHeaders.REQUEST_ID), is("reqid"));
  }
}

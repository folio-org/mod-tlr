package org.folio.service.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;

class CheckOutServiceImplTest {

  @Test
  void shouldReturnAllHeaders() {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.addHeader(XOkapiHeaders.PERMISSIONS, "test-permission");
    request.addHeader(XOkapiHeaders.REQUEST_ID, "request-id-value");
    var attrs = new org.springframework.web.context.request.ServletRequestAttributes(request);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);

    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null);
    Map<String, String> headers = service.getHeadersFromContext();

    assertThat(headers.get(XOkapiHeaders.PERMISSIONS), is("test-permission"));
    assertThat(headers.get(XOkapiHeaders.REQUEST_ID), is("request-id-value"));
  }

  @Test
  void shouldReturnEmptyMapIfNoHeaders() {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    var attrs = new org.springframework.web.context.request.ServletRequestAttributes(request);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null);
    Map<String, String> headers = service.getHeadersFromContext();
    assertThat(headers.isEmpty(), is(true));
  }

  @Test
  void shouldReturnOnlyRelevantHeaders() {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.addHeader(XOkapiHeaders.TENANT, "tenant");
    request.addHeader(XOkapiHeaders.PERMISSIONS, "perm");
    request.addHeader(XOkapiHeaders.REQUEST_ID, "reqid");
    var attrs = new org.springframework.web.context.request.ServletRequestAttributes(request);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);
    CheckOutServiceImpl service = new CheckOutServiceImpl(null, null, null, null, null, null);
    Map<String, String> headers = service.getHeadersFromContext();
    assertThat(headers.size(), is(2));
    assertThat(headers.get(XOkapiHeaders.PERMISSIONS), is("perm"));
    assertThat(headers.get(XOkapiHeaders.REQUEST_ID), is("reqid"));
  }
}

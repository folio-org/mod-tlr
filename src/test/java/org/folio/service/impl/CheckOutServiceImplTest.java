package org.folio.service.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

class CheckOutServiceImplTest {

  @Test
  void shouldReturnAllHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String testTenantValue = "test-tenant";
    String customValue = "custom-value";
    request.addHeader("x-okapi-tenant", testTenantValue);
    request.addHeader("X-Custom-Header", customValue);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    CheckOutServiceImpl service = Mockito.mock(CheckOutServiceImpl.class, Mockito.CALLS_REAL_METHODS);
    Map<String, String> headers = service.extractHeaders();

    assertThat(headers.get("x-okapi-tenant"), is(testTenantValue));
    assertThat(headers.get("X-Custom-Header"), is(customValue));
  }

  @Test
  void shouldReturnEmptyMapIfNoRequestAttributes() {
    RequestContextHolder.resetRequestAttributes();
    CheckOutServiceImpl service = Mockito.mock(CheckOutServiceImpl.class, Mockito.CALLS_REAL_METHODS);
    Map<String, String> headers = service.extractHeaders();
    assertThat(headers, is(anEmptyMap()));
  }
}


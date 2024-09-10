package org.folio.util;

import static org.folio.util.TestUtils.buildToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.Cookie;

class HttpUtilsTest {

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void tenantIsExtractedFromCookies() {
    String tenantFromCookies = "tenant_from_cookies";
    String tenantFromHeaders = "tenant_from_headers";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("folioAccessToken", buildToken(tenantFromCookies)));
    request.addHeader("x-okapi-token", buildToken(tenantFromHeaders));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    String tenantFromToken = HttpUtils.getTenantFromToken().orElseThrow();
    assertEquals(tenantFromCookies, tenantFromToken);
  }

  @Test
  void tenantIsExtractedFromHeaders() {
    String tenantFromHeaders = "tenant_from_headers";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("x-okapi-token", buildToken(tenantFromHeaders));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    String tenantFromToken = HttpUtils.getTenantFromToken().orElseThrow();
    assertEquals(tenantFromHeaders, tenantFromToken);
  }

  @Test
  void tenantIsNotFound() {
    RequestContextHolder.setRequestAttributes(
      new ServletRequestAttributes(new MockHttpServletRequest()));
    assertTrue(HttpUtils.getTenantFromToken().isEmpty());
  }

}
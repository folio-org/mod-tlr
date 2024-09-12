package org.folio.util;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@UtilityClass
@Log4j2
public class HttpUtils {
  private static final String ACCESS_TOKEN_COOKIE_NAME = "folioAccessToken";
  private static final String TOKEN_SECTION_SEPARATOR = "\\.";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static Optional<String> getTenantFromToken() {
    return getCurrentRequest()
      .flatMap(HttpUtils::getToken)
      .flatMap(HttpUtils::extractTenantFromToken);
  }

  public static Optional<HttpServletRequest> getCurrentRequest() {
    return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
      .filter(ServletRequestAttributes.class::isInstance)
      .map(ServletRequestAttributes.class::cast)
      .map(ServletRequestAttributes::getRequest);
  }

  private static Optional<String> getToken(HttpServletRequest request) {
    return getCookie(request, ACCESS_TOKEN_COOKIE_NAME)
      .or(() -> getHeader(request, XOkapiHeaders.TOKEN));
  }

  private static Optional<String> getHeader(HttpServletRequest request, String headerName) {
    log.info("getHeader:: looking for header '{}'", headerName);
    return Optional.ofNullable(request.getHeader(headerName));
  }

  private static Optional<String> getCookie(HttpServletRequest request, String cookieName) {
    log.info("getCookie:: looking for cookie '{}'", cookieName);
    return Optional.ofNullable(request)
      .map(HttpServletRequest::getCookies)
      .flatMap(cookies -> getCookie(cookies, cookieName))
      .map(Cookie::getValue);
  }

  private static Optional<Cookie> getCookie(Cookie[] cookies, String cookieName) {
    return Arrays.stream(cookies)
      .filter(cookie -> StringUtils.equals(cookie.getName(), cookieName))
      .findFirst();
  }

  private static Optional<String> extractTenantFromToken(String token) {
    log.info("extractTenantFromToken:: extracting tenant ID from token");
    try {
      byte[] decodedPayload = Base64.getDecoder()
        .decode(token.split(TOKEN_SECTION_SEPARATOR)[1]);
      String tenantId = OBJECT_MAPPER.readTree(decodedPayload)
        .get("tenant")
        .asText();

      log.info("extractTenantFromToken:: successfully extracted tenant ID from token: {}", tenantId);
      return Optional.ofNullable(tenantId);
    } catch (Exception e) {
      log.error("getTenantFromToken:: failed to extract tenant ID from token", e);
      return Optional.empty();
    }
  }

}

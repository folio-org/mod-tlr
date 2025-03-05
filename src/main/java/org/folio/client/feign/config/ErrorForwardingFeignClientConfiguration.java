package org.folio.client.feign.config;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.folio.exception.HttpFailureFeignException;
import org.springframework.context.annotation.Bean;

import feign.Request;
import feign.codec.ErrorDecoder;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ErrorForwardingFeignClientConfiguration {

  @Bean
  public ErrorDecoder errorDecoder() {
    return (methodKey, response) -> {
      int status = response.status();
      String url = Optional.of(response.request())
        .map(Request::url)
        .orElse(null);

      log.warn("errorDecoder:: handling error response with status {} from {}", status, url);
      String body = null;
      try {
        body = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.debug("errorDecoder:: body: {}", body);
      } catch (Exception e) {
        log.error("errorDecoder:: failed to decode response body", e);
      }

      return new HttpFailureFeignException(url, status, body);
    };
  }

}

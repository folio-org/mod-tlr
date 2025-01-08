package org.folio.client.feign;

import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.context.annotation.Bean;

import feign.codec.ErrorDecoder;

public class CustomFeignClientConfiguration extends FeignClientConfiguration {
  @Bean
  public ErrorDecoder errorDecoder() {
    return new CustomFeignErrorDecoder();
  }
}

package org.folio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

@Configuration
public class FeignConfiguration {
  @Bean
  Logger.Level feignLoggerLevel() {
    return Logger.Level.FULL;
  }

//  @Bean
//  public ObjectMapper objectMapper() {
//    return new ObjectMapper()
//      .findAndRegisterModules(); // This ensures that object mapper auto-detects all data modules (like time data types)
//  }

//  @Bean
//  public Encoder feignEncoder(ObjectMapper objectMapper) {
//    return new JacksonEncoder(objectMapper);
//  }

//  @Bean
//  public Decoder feignDecoder(ObjectMapper objectMapper) {
//    return new JacksonDecoder(objectMapper);
//  }
}

package org.folio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties("spring.kafka")
public class KafkaProperties {
  private String bootstrapServers;
  private KafkaConsumerProperties consumer;

  @Data
  public static class KafkaConsumerProperties {
    private String groupId;
    private String autoOffsetReset;
  }

}


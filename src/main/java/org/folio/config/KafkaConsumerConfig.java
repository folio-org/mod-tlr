package org.folio.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@Configuration
public class KafkaConsumerConfig {

  @Value(value = "${kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value(value = "${kafka.consumer.group-id}")
  private String consumerGroupId;

  @Value(value = "${kafka.consumer.auto-offset-reset}")
  private String autoResetOffset;

  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> consumerConfig = Map.of(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
      ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId,
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoResetOffset,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    return new DefaultKafkaConsumerFactory<>(consumerConfig);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());

    return factory;
  }

}
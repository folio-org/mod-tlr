package org.folio.client;

import org.folio.domain.dto.ConsortiaConfiguration;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "consortia-configuration")
public interface ConsortiaConfigurationClient {

  @GetExchange
  ConsortiaConfiguration getConfiguration();
}

package org.folio.client.feign;

import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "publications", url = "publications", configuration = FeignClientConfiguration.class)
public interface PublishCoordinatorClient {

  @PostMapping()
  PublicationResponse publish(@RequestBody PublicationRequest publicationRequest);
}

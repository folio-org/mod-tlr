package org.folio.client.feign;

import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.domain.dto.SharingInstance;
import org.folio.domain.dto.TenantCollection;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "consortia", url = "consortia", configuration = FeignClientConfiguration.class)
public interface ConsortiaClient {

  @GetMapping(value = "/{consortiumId}/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
  TenantCollection getConsortiaTenants(@PathVariable String consortiumId);

  @PostMapping(value = "/{consortiumId}/publications", consumes = MediaType.APPLICATION_JSON_VALUE)
  PublicationResponse postPublications(@PathVariable String consortiumId,
    @RequestBody PublicationRequest publicationRequest);

  @PostMapping(value = "/{consortiumId}/sharing/instances", produces = MediaType.APPLICATION_JSON_VALUE)
  SharingInstance shareInstance(@PathVariable String consortiumId,
    @RequestBody SharingInstance sharingInstance);
}

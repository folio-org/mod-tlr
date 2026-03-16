package org.folio.client;

import org.folio.domain.dto.PublicationRequest;
import org.folio.domain.dto.PublicationResponse;
import org.folio.domain.dto.SharingInstance;
import org.folio.domain.dto.TenantCollection;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "consortia")
public interface ConsortiaClient {

  @GetExchange(value = "/{consortiumId}/tenants", accept = MediaType.APPLICATION_JSON_VALUE)
  TenantCollection getConsortiaTenants(@PathVariable String consortiumId);

  @PostExchange(value = "/{consortiumId}/publications", contentType = MediaType.APPLICATION_JSON_VALUE)
  PublicationResponse postPublications(@PathVariable String consortiumId,
    @RequestBody PublicationRequest publicationRequest);

  @PostExchange(value = "/{consortiumId}/sharing/instances", accept = MediaType.APPLICATION_JSON_VALUE)
  SharingInstance shareInstance(@PathVariable String consortiumId,
    @RequestBody SharingInstance sharingInstance);
}

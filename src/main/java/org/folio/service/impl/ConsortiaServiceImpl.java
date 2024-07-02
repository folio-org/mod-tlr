package org.folio.service.impl;

import org.folio.client.feign.ConsortiaClient;
import org.folio.domain.dto.TenantCollection;
import org.folio.service.ConsortiaService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiaServiceImpl implements ConsortiaService {
  private final ConsortiaClient consortiaClient;

  @Override
  public TenantCollection getAllDataTenants(String consortiumId) {
    return consortiaClient.getConsortiaTenants(consortiumId);
  }
}

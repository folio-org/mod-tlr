package org.folio.service.impl;

import org.folio.client.CancellationReasonClient;
import org.folio.domain.dto.CancellationReason;
import org.folio.service.CancellationReasonService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class CancellationReasonServiceImpl implements CancellationReasonService {

  private final CancellationReasonClient cancellationReasonClient;

  @Override
  public CancellationReason find(String cancellationReasonId) {
    log.info("find:: looking up cancellation reason {}", cancellationReasonId);
    CancellationReason cancellationReason = cancellationReasonClient.getCancellationReason(cancellationReasonId);
    log.info("find:: found cancellation reason {}", cancellationReasonId);
    return cancellationReason;
  }
}

package org.folio.service.impl;

import org.folio.client.feign.LoanPolicyClient;
import org.folio.domain.dto.LoanPolicy;
import org.folio.service.LoanPolicyService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoanPolicyServiceImpl implements LoanPolicyService {

  private final LoanPolicyClient loanPolicyClient;

  @Override
  public LoanPolicy find(String loanPolicyId) {
    log.info("find:: looking up loanPolicy {}", loanPolicyId);
    return loanPolicyClient.get(loanPolicyId);
  }

  @Override
  public LoanPolicy create(LoanPolicy loanPolicy) {
    log.info("create:: creating loanPolicy {}", loanPolicy.getId());
    return loanPolicyClient.post(loanPolicy);
  }
}

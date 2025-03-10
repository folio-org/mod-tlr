package org.folio.service.impl;

import org.folio.domain.dto.LoanPolicy;
import org.folio.service.LoanPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class LoanPolicyCloningServiceImpl extends CloningServiceImpl<LoanPolicy> {
  private static final String COPY_LOAN_POLICY_PREFIX = "COPY_OF_%s";
  private final LoanPolicyService loanPolicyService;

  public LoanPolicyCloningServiceImpl(@Autowired LoanPolicyService loanPolicyService) {

    super(LoanPolicy::getId);
    this.loanPolicyService = loanPolicyService;
  }

  @Override
  protected LoanPolicy find(String loanPolicyId) {
    return loanPolicyService.find(loanPolicyId);
  }

  @Override
  protected LoanPolicy create(LoanPolicy clone) {
    return loanPolicyService.create(clone);
  }

  @Override
  protected LoanPolicy buildClone(LoanPolicy loanPolicy) {
    return loanPolicy.name(String.format(COPY_LOAN_POLICY_PREFIX, loanPolicy.getName()));
  }
}

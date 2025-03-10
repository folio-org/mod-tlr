package org.folio.service;

import org.folio.domain.dto.LoanPolicy;

public interface LoanPolicyService {
  LoanPolicy find(String loanPolicyId);
  LoanPolicy create(LoanPolicy loanPolicy);
}

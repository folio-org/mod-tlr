package org.folio.service;

import java.util.Optional;

import org.folio.domain.dto.Loan;

public interface LoanService {
  Optional<Loan> fetchLoan(String loanId);
  Optional<Loan> findOpenLoan(String userId, String itemId);
}

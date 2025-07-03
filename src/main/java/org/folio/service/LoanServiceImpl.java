package org.folio.service;

import static org.folio.support.CqlQuery.exactMatch;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.folio.client.feign.LoanStorageClient;
import org.folio.domain.dto.Loan;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoanServiceImpl implements LoanService {

  private final LoanStorageClient loanStorageClient;

  @Override
  public Loan fetchLoan(String loanId) {
    log.info("fetchLoan:: fetching loan {}", loanId);
    return loanStorageClient.getLoan(loanId);
  }

  @Override
  public Optional<Loan> findOpenLoan(String userId, String itemId) {
    CqlQuery query = exactMatch("userId", userId)
      .and(exactMatch("itemId", itemId))
      .and(exactMatch("status.name", "Open"));

    return findLoans(query, 1)
      .stream()
      .findFirst();
  }

  private Collection<Loan> findLoans(CqlQuery query, int limit) {
    log.info("findLoans:: looking for loans by query: {}", query);
    List<Loan> loans = loanStorageClient.getByQuery(query, limit).getLoans();
    log.info("findLoans:: {} loans found", loans::size);
    return loans;
  }
}

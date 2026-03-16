package org.folio.client;

import java.util.Optional;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Loans;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "loan-storage/loans")
public interface LoanStorageClient extends GetByQueryClient<Loans> {

  @Override
  @GetExchange
  Loans getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @GetExchange("/{loanId}")
  Optional<Loan> getLoan(@PathVariable String loanId);

  @PutExchange("/{loanId}")
  Loan updateLoan(@PathVariable String loanId, @RequestBody Loan loan);
}

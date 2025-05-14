package org.folio.client.feign;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Loans;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "loan-storage", url = "loan-storage/loans", configuration = FeignClientConfiguration.class)
public interface LoanStorageClient extends GetByQueryClient<Loans> {

  @GetMapping("/{loanId}")
  Loan getLoan(@PathVariable String loanId);

  @PutMapping("/{loanId}")
  Loan updateLoan(@PathVariable String loanId, @RequestBody Loan loan);
}

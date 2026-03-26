package org.folio.client;

import org.folio.domain.dto.LoanPolicy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "loan-policy-storage/loan-policies")
public interface LoanPolicyClient {

  @GetExchange("/{id}")
  LoanPolicy get(@PathVariable String id);

  @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
  LoanPolicy post(@RequestBody LoanPolicy loanPolicy);
}

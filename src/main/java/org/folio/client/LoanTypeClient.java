package org.folio.client;

import org.folio.domain.dto.LoanTypes;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "loan-types")
public interface LoanTypeClient extends GetByQueryClient<LoanTypes> {

  @Override
  @GetExchange
  LoanTypes getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}

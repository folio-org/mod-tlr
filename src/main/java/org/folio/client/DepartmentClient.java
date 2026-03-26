package org.folio.client;

import org.folio.domain.dto.Departments;
import org.folio.support.CqlQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "departments")
public interface DepartmentClient extends GetByQueryClient<Departments> {

  @Override
  @GetExchange
  Departments getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

}

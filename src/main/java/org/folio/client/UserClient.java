package org.folio.client;

import org.folio.domain.dto.User;
import org.folio.domain.dto.Users;
import org.folio.support.CqlQuery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "users")
public interface UserClient extends GetByQueryClient<Users> {

  @Override
  @GetExchange
  Users getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
  User postUser(@RequestBody User user);

  @GetExchange("/{userId}")
  User getUser(@PathVariable String userId);

  @PutExchange("/{userId}")
  User putUser(@PathVariable String userId, @RequestBody User user);
}

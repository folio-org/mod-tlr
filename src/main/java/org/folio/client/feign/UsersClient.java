package org.folio.client.feign;

import org.folio.domain.dto.User;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "users", url = "users", configuration = FeignClientConfiguration.class)
public interface UsersClient {

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  User postUser(@RequestBody User user);

  @GetMapping("/{userId}")
  User getUser(@PathVariable String userId);
}

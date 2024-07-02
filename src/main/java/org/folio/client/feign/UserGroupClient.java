package org.folio.client.feign;

import org.folio.domain.dto.UserGroup;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "groups", url = "groups", configuration = FeignClientConfiguration.class)
public interface UserGroupClient {

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  UserGroup postUserGroup(@RequestBody UserGroup userGroup);

  @PutMapping("/{groupId}")
  UserGroup putUserGroup(@PathVariable String groupId, @RequestBody UserGroup userGroup);
}

package org.folio.client;

import org.folio.domain.dto.UserGroup;
import org.folio.domain.dto.UserGroups;
import org.folio.support.CqlQuery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange(url = "groups")
public interface UserGroupClient extends GetByQueryClient<UserGroups> {

  @Override
  @GetExchange
  UserGroups getByQuery(@RequestParam CqlQuery query, @RequestParam int limit);

  @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
  UserGroup postUserGroup(@RequestBody UserGroup userGroup);

  @PutExchange("/{groupId}")
  UserGroup putUserGroup(@PathVariable String groupId, @RequestBody UserGroup userGroup);
}

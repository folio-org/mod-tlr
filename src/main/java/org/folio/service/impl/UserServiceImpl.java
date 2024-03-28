package org.folio.service.impl;

import org.folio.client.feign.UsersClient;
import org.folio.domain.dto.User;
import org.folio.service.UserService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class UserServiceImpl implements UserService {

  private final UsersClient usersClient;

  @Override
  public User find(String userId) {
    log.info("find:: looking up user {}", userId);
    return usersClient.getUser(userId);
  }

  @Override
  public User create(User user) {
    log.info("create:: creating user {}", user.getId());
    return usersClient.postUser(user);
  }

}

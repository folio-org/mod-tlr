package org.folio.service.impl;

import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.domain.dto.UserType;
import org.folio.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class UserReplicationServiceImpl extends ReplicationServiceImpl<User> {

  private final UserService userService;

  public UserReplicationServiceImpl(@Autowired UserService userService) {

    super(User::getId);
    this.userService = userService;
  }

  @Override
  protected User find(String userId) {
    return userService.find(userId);
  }

  @Override
  protected User create(User replica) {
    return userService.create(replica);
  }

  @Override
  protected User buildReplica(User original) {
    User replica = new User()
      .id(original.getId())
      .username(original.getUsername())
      .patronGroup(original.getPatronGroup())
      .type(UserType.SHADOW.getValue())
      .active(true);

    UserPersonal personal = original.getPersonal();
    if (personal != null) {
      replica.setPersonal(new UserPersonal()
        .firstName(personal.getFirstName())
        .lastName(personal.getLastName())
      );
    }
    log.debug("buildReplica:: result: {}", () -> replica);
    return replica;
  }
}

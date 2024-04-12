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
public class UserCloningServiceImpl extends CloningServiceImpl<User> {

  private final UserService userService;

  public UserCloningServiceImpl(@Autowired UserService userService) {

    super(User::getId);
    this.userService = userService;
  }

  @Override
  protected User find(String userId) {
    return userService.find(userId);
  }

  @Override
  protected User create(User clone) {
    return userService.create(clone);
  }

  @Override
  protected User buildClone(User original) {
    User clone = new User()
      .id(original.getId())
      .patronGroup(original.getPatronGroup())
      .type(UserType.SHADOW.getValue())
      .barcode(original.getBarcode())
      .active(true);

    UserPersonal personal = original.getPersonal();
    if (personal != null) {
      clone.setPersonal(new UserPersonal()
        .firstName(personal.getFirstName())
        .lastName(personal.getLastName())
      );
    }
    log.debug("buildClone:: result: {}", () -> clone);
    return clone;
  }
}

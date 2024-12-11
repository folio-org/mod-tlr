package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.service.impl.UserCloningServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;
import feign.Request;

@ExtendWith(MockitoExtension.class)
class UserCloningServiceTest {

  @Spy
  private UserService userService;

  private CloningService<User> userCloningService;

  @Captor
  ArgumentCaptor<User> userCaptor;

  @Test
  void securePatronNameShouldBeCopied() {
    userCloningService = new UserCloningServiceImpl(userService);

    doThrow(new FeignException.NotFound(null, Request.create(Request.HttpMethod.GET, "", Map.of(),
      Request.Body.create(""), null), null, null))
      .when(userService)
      .find(any(String.class));
    when(userService.create(any(User.class))).thenReturn(null);

    userCloningService.clone(new User()
      .id(UUID.randomUUID().toString())
      .personal(new UserPersonal()
        .firstName("Secure")
        .lastName("Patron")));

    verify(userService).create(userCaptor.capture());

    assertEquals("Secure", userCaptor.getValue().getPersonal().getFirstName());
    assertEquals("Patron", userCaptor.getValue().getPersonal().getLastName());
  }
}

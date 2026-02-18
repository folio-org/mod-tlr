package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.folio.client.feign.UserClient;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.service.impl.UserCloningServiceImpl;
import org.folio.service.impl.UserServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;
import feign.Request;

@ExtendWith(MockitoExtension.class)
class UserCloningServiceTest {
  @Mock
  UserClient userClient;

  @Mock
  SystemUserScopedExecutionService systemUserScopedExecutionService;

  @InjectMocks
  UserServiceImpl userService;

  CloningService<User> userCloningService;

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

  @Test
  void whenPatronAlreadyExistsDuringCloningErrorShouldBeHandledAndFindRepeated() {
    userService = Mockito.spy(new UserServiceImpl(userClient, systemUserScopedExecutionService));
    userCloningService = new UserCloningServiceImpl(userService);

    doThrow(new FeignException.UnprocessableEntity("Feign error",
      Request.create(Request.HttpMethod.POST, "", Map.of(), Request.Body.create(""), null), """
      {
            "errors": [
                {
                    "message": "User with this id already exists",
                    "type": "1",
                    "code": "-1",
                    "parameters": [
                        {
                            "key": "id",
                            "value": "ea60c80a-f369-4982-b481-22275870c670"
                        }
                    ]
                }
            ]
        }
      """.getBytes(), null))
      .when(userClient)
      .postUser(any());

    doThrow(new FeignException.NotFound(null, Request.create(Request.HttpMethod.GET, "", Map.of(),
      Request.Body.create(""), null), null, null))
      .doReturn(new User()
        .id(UUID.randomUUID().toString())
        .personal(new UserPersonal()
          .firstName("ExistingFirstName")
          .lastName("ExistingLastName")))
      .when(userClient)
      .getUser(any(String.class));

    var clonedUser = userCloningService.clone(new User()
      .id(UUID.randomUUID().toString())
      .personal(new UserPersonal()
        .firstName("FirstName")
        .lastName("LastName")));

    verify(userService, Mockito.times(2)).find(any(String.class));

    assertEquals("ExistingFirstName", clonedUser.getPersonal().getFirstName());
    assertEquals("ExistingLastName", clonedUser.getPersonal().getLastName());
  }

  @Test
  void whenPatronAlreadyExistsButErrorIsDifferentShouldThrowAndNotRepeatFind() {
    userService = Mockito.spy(new UserServiceImpl(userClient, systemUserScopedExecutionService));
    userCloningService = new UserCloningServiceImpl(userService);

    doThrow(new FeignException.UnprocessableEntity("Feign error",
      Request.create(Request.HttpMethod.POST, "", Map.of(), Request.Body.create(""), null), """
      {
            "errors": [
                {
                    "message": "Some other error",
                    "type": "1",
                    "code": "-1",
                    "parameters": [
                        {
                            "key": "id",
                            "value": "ea60c80a-f369-4982-b481-22275870c670"
                        }
                    ]
                }
            ]
        }
      """.getBytes(), null))
      .when(userClient)
      .postUser(any());

    doThrow(new FeignException.NotFound(null, Request.create(Request.HttpMethod.GET, "", Map.of(),
      Request.Body.create(""), null), null, null))
      .when(userClient)
      .getUser(any(String.class));

    assertThrows(FeignException.UnprocessableEntity.class, () -> userCloningService.clone(new User()
      .id(UUID.randomUUID().toString())
      .personal(new UserPersonal()
        .firstName("FirstName")
        .lastName("LastName"))));

    verify(userService, Mockito.times(1)).find(any(String.class));
  }
}

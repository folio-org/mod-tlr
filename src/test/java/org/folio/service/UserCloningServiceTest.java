package org.folio.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.EMPTY;

import java.util.UUID;

import org.folio.client.UserClient;
import org.folio.domain.dto.User;
import org.folio.domain.dto.UserPersonal;
import org.folio.service.impl.UserCloningServiceImpl;
import org.folio.service.impl.UserServiceImpl;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.scope.FolioExecutionContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class UserCloningServiceTest {
  @Mock
  UserClient userClient;

  @Mock
  FolioExecutionContextService contextService;

  @Mock
  FolioExecutionContext folioContext;

  @InjectMocks
  UserServiceImpl userService;

  @Mock
  UserServiceImpl userServiceForSecurePatron;

  CloningService<User> userCloningService;

  @Captor
  ArgumentCaptor<User> userCaptor;

  @Test
  void securePatronNameShouldBeCopied() {
    userCloningService = new UserCloningServiceImpl(userServiceForSecurePatron);

    when(userServiceForSecurePatron.find(any(String.class))).thenReturn(null);
    when(userServiceForSecurePatron.create(any(User.class))).thenReturn(null);

    userCloningService.clone(new User()
      .id(UUID.randomUUID().toString())
      .personal(new UserPersonal()
        .firstName("Secure")
        .lastName("Patron")));

    verify(userServiceForSecurePatron).create(userCaptor.capture());

    assertEquals("Secure", userCaptor.getValue().getPersonal().getFirstName());
    assertEquals("Patron", userCaptor.getValue().getPersonal().getLastName());
  }

  @Test
  void whenPatronAlreadyExistsDuringCloningErrorShouldBeHandledAndFindRepeated() {
    userService = Mockito.spy(new UserServiceImpl(userClient, contextService, folioContext));
    userCloningService = new UserCloningServiceImpl(userService);

    doThrow(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable Entity",
      EMPTY, """
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
      """.getBytes(), UTF_8))
      .when(userClient)
      .postUser(any());

    when(userClient.getUser(any(String.class)))
      .thenReturn(null)
      .thenReturn(new User()
        .id(UUID.randomUUID().toString())
        .personal(new UserPersonal()
          .firstName("ExistingFirstName")
          .lastName("ExistingLastName")));

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
    userService = Mockito.spy(new UserServiceImpl(userClient, contextService, folioContext));
    userCloningService = new UserCloningServiceImpl(userService);

    doThrow(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable Entity",
      EMPTY, """
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
      """.getBytes(), UTF_8))
      .when(userClient)
      .postUser(any());

    when(userClient.getUser(any(String.class))).thenReturn(null);

    assertThrows(HttpClientErrorException.class, () -> userCloningService.clone(new User()
      .id(UUID.randomUUID().toString())
      .personal(new UserPersonal()
        .firstName("FirstName")
        .lastName("LastName"))));

    verify(userService, Mockito.times(1)).find(any(String.class));
  }
}

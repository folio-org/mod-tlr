package org.folio.service;

import org.folio.domain.dto.TitleLevelRequest;
import org.folio.domain.entity.TitleLevelRequestEntity;
import org.folio.domain.mapper.TitleLevelRequestMapper;
import org.folio.repository.TitleLevelRequestsRepository;
import org.folio.service.impl.TitleLevelRequestsServiceImpl;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class TitleLevelRequestsServiceTest {

  @InjectMocks
  private TitleLevelRequestsServiceImpl titleLevelRequestsService;
  @Mock
  private TitleLevelRequestsRepository titleLevelRequestsRepository;
  @Mock
  private TitleLevelRequestMapper titleLevelRequestMapper;

  @Test
  void getById() {
    titleLevelRequestsService.get(any());
    verify(titleLevelRequestsRepository).findById(any());
  }

  @Test
  void postTitleLevelRequest() {
    var instanceId = UUID.randomUUID();
    var requesterId = UUID.randomUUID();
    var pickupServicePointId = UUID.randomUUID();
    var requestType = TitleLevelRequest.RequestTypeEnum.PAGE;
    var requestLevel = TitleLevelRequest.RequestLevelEnum.TITLE;
    var fulfillmentPreference = TitleLevelRequest.FulfillmentPreferenceEnum.HOLD_SHELF;
    var requestExpirationDate = DateTime.now().toDate();
    var patronComments = "Test comment";

    var mockRequestEntity = new TitleLevelRequestEntity();
    mockRequestEntity.setInstanceId(instanceId);
    mockRequestEntity.setRequesterId(requesterId);
    mockRequestEntity.setRequestType(requestType.getValue());
    mockRequestEntity.setRequestLevel(requestLevel.getValue());
    mockRequestEntity.setRequestExpirationDate(requestExpirationDate.toString());
    mockRequestEntity.setPatronComments(patronComments);
    mockRequestEntity.setFulfillmentPreference(fulfillmentPreference.getValue());
    mockRequestEntity.setPickupServicePointId(pickupServicePointId);

    var mockRequest = new TitleLevelRequest();
    mockRequest.setInstanceId(instanceId.toString());
    mockRequest.setRequesterId(requesterId.toString());
    mockRequest.setRequestType(requestType);
    mockRequest.setRequestLevel(requestLevel);
    mockRequest.setRequestExpirationDate(requestExpirationDate);
    mockRequest.setPatronComments(patronComments);
    mockRequest.setFulfillmentPreference(fulfillmentPreference);
    mockRequest.setPickupServicePointId(pickupServicePointId.toString());

    when(titleLevelRequestMapper.mapDtoToEntity(any(TitleLevelRequest.class))).thenReturn(mockRequestEntity);
    when(titleLevelRequestMapper.mapEntityToDto(any(TitleLevelRequestEntity.class))).thenReturn(mockRequest);
    when(titleLevelRequestsRepository.save(any(TitleLevelRequestEntity.class))).thenReturn(mockRequestEntity);

    var postTitleLevelRequest = titleLevelRequestsService.post(mockRequest);

    assertEquals(instanceId.toString(), postTitleLevelRequest.getInstanceId());
    assertEquals(requesterId.toString(), postTitleLevelRequest.getRequesterId());
    assertEquals(requestType, postTitleLevelRequest.getRequestType());
    assertEquals(requestExpirationDate, postTitleLevelRequest.getRequestExpirationDate());
    assertEquals(patronComments, postTitleLevelRequest.getPatronComments());
    assertEquals(fulfillmentPreference, postTitleLevelRequest.getFulfillmentPreference());
    assertEquals(pickupServicePointId.toString(), postTitleLevelRequest.getPickupServicePointId());
  }
}

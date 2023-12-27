package org.folio.mr.service;

import org.folio.mr.domain.mapper.TitleLevelRequestMapper;
import org.folio.mr.repository.TitleLevelRequestsRepository;
import org.folio.mr.service.impl.TitleLevelRequestsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

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
}

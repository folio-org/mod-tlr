package org.folio.service.impl;

import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.Request.RequestTypeEnum.PAGE;
import static org.folio.domain.dto.Request.StatusEnum.OPEN_NOT_YET_FILLED;

import java.util.EnumSet;

import org.folio.service.AddressTypeService;
import org.folio.service.ConsortiaService;
import org.folio.service.DepartmentService;
import org.folio.service.ItemService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class PickSlipsService extends StaffSlipsServiceImpl {

  public PickSlipsService(@Autowired LocationService locationService,
    @Autowired ItemService itemService, @Autowired RequestService requestService,
    @Autowired ConsortiaService consortiaService,
    @Autowired SystemUserScopedExecutionService executionService, @Autowired UserService userService,
    @Autowired DepartmentService departmentService, @Autowired AddressTypeService addressTypeService) {

    super(EnumSet.of(PAGED), EnumSet.of(OPEN_NOT_YET_FILLED), EnumSet.of(PAGE), locationService,
      itemService, requestService, consortiaService, executionService, userService, departmentService,
      addressTypeService);
  }
}

package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.DepartmentClient;
import org.folio.domain.dto.Department;
import org.folio.domain.dto.Departments;
import org.folio.service.DepartmentService;
import org.folio.support.BulkFetcher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

  private final DepartmentClient departmentClient;

  @Override
  public Collection<Department> findDepartments(Collection<String> ids) {
    log.info("findDepartments:: fetching departments by {} IDs", ids.size());
    log.debug("findDepartments:: ids={}", ids);

    return BulkFetcher.fetch(departmentClient, ids, Departments::getDepartments);
  }
}

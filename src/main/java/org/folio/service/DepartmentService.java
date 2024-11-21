package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.Department;

public interface DepartmentService {
  Collection<Department> findDepartments(Collection<String> ids);
}

package org.folio.client.feign;

import org.folio.domain.dto.Departments;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "departments", url = "departments", configuration = FeignClientConfiguration.class)
public interface DepartmentClient extends GetByQueryClient<Departments> {

}

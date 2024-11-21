package org.folio.client.feign;

import org.folio.domain.dto.AddressTypes;
import org.folio.spring.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "address-types", url = "addresstypes", configuration = FeignClientConfiguration.class)
public interface AddressTypeClient extends GetByQueryClient<AddressTypes> {

}

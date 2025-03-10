package org.folio.service;

import org.folio.domain.dto.CheckOutRequest;
import org.folio.domain.dto.CheckOutResponse;

public interface CheckOutService {

  CheckOutResponse checkOut(CheckOutRequest request);

}

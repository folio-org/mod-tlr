package org.folio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Loans;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Requests;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.support.MockDataUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class LoanActionBaseIT extends BaseIT {

  static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  static final UUID LOCAL_TENANT_LOAN_ID = UUID.randomUUID();
  static final UUID LENDING_TENANT_LOAN_ID = UUID.randomUUID();
  static final UUID USER_ID = UUID.randomUUID();
  static final UUID ITEM_ID = UUID.randomUUID();
  static final UUID ECS_REQUEST_ID = UUID.randomUUID();

  static final String LOAN_STORAGE_URL = "/loan-storage/loans";
  static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  static final String LOAN_QUERY_TEMPLATE =
    "userId==\"%s\" and (itemId==\"%s\") and (status.name==\"Open\")";
  static final String ACTION_COMMENT = "Test comment";
  static final String INVALID_REQUEST_ERROR_MESSAGE =
    "Invalid request: must have either loanId or (itemId and userId)";

  @Autowired
  private EcsTlrRepository ecsTlrRepository;

  void setupLocalLoanMock(Date loanCreationDate, String loanId) {
    Loan mockLocalLoan = MockDataUtils.buildLoan(
      LOCAL_TENANT_LOAN_ID, USER_ID, ITEM_ID, loanCreationDate);

    wireMockServer.stubFor(get(urlEqualTo(LOAN_STORAGE_URL + "/" + loanId))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockLocalLoan))));
  }

  void setupLocalLoanMockWithQuery(Date loanCreationDate) {
    Loan mockLocalLoan = MockDataUtils.buildLoan(
      LOCAL_TENANT_LOAN_ID, USER_ID, ITEM_ID, loanCreationDate);
    Loans mockLocalLoans = new Loans()
      .loans(List.of(mockLocalLoan))
      .totalRecords(1);

    wireMockServer.stubFor(get(urlPathEqualTo(LOAN_STORAGE_URL))
      .withQueryParam("query", equalTo(LOAN_QUERY_TEMPLATE.formatted(USER_ID, ITEM_ID)))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(mockLocalLoans))));
  }

  void setupLendingTenantLoanMock(Date loanCreationDate) {
    Loan mockLendingTenantLoan = MockDataUtils.buildLoan(
      LENDING_TENANT_LOAN_ID, USER_ID, ITEM_ID, loanCreationDate);

    wireMockServer.stubFor(get(urlPathEqualTo(LOAN_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_COLLEGE))
      .willReturn(okJson(asJsonString(new Loans().addLoansItem(mockLendingTenantLoan)))));
  }

  void setupEcsRequestMock(Date requestUpdateDate) {
    Request mockEcsRequest = MockDataUtils.buildEcsRequest(
      ECS_REQUEST_ID, USER_ID, ITEM_ID, requestUpdateDate,
      Request.EcsRequestPhaseEnum.PRIMARY, Request.StatusEnum.CLOSED_FILLED);

    wireMockServer.stubFor(get(urlPathEqualTo(REQUEST_STORAGE_URL))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(okJson(asJsonString(new Requests().addRequestsItem(mockEcsRequest)))));
  }

  void mockEcsTlrEntity() {
    EcsTlrEntity ecsTlr = MockDataUtils.buildEcsTlrEntity(ITEM_ID, USER_ID, ECS_REQUEST_ID,
      ECS_REQUEST_ID, TENANT_ID_CONSORTIUM, TENANT_ID_COLLEGE);
    ecsTlrRepository.save(ecsTlr);
  }

  void mockErrorCode(String url, int code) {
    wireMockServer.stubFor(post(urlEqualTo(url))
      .withHeader(TENANT, equalTo(TENANT_ID_CONSORTIUM))
      .willReturn(aResponse().withStatus(code)));
  }

}


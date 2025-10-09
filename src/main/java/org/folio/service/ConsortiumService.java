package org.folio.service;

public interface ConsortiumService {
  String getCurrentTenantId();
  String getCurrentConsortiumId();
  String getCentralTenantId();
  boolean isCurrentTenantCentral();
  boolean isCentralTenant(String tenantId);
  boolean isCurrentTenantConsortiumMember();
}

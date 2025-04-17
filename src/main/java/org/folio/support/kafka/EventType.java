package org.folio.support.kafka;

public enum EventType {
  UPDATE,
  DELETE,
  CREATE,
  DELETE_ALL,
  REINDEX,
  ITERATE,
  MIGRATION;
}

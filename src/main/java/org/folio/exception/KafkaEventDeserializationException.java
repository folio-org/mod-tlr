package org.folio.exception;

public class KafkaEventDeserializationException extends RuntimeException {
  public KafkaEventDeserializationException(Throwable cause) {
    super(cause);
  }
}

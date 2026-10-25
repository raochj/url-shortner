package com.schwab.urlshortener.exception;

public class DuplicateAliasException extends RuntimeException {
  public DuplicateAliasException(String message) {
    super(message);
  }

  public DuplicateAliasException(String alias, String field) {
    super("Duplicate " + field + ": '" + alias + "' is already in use");
  }
}

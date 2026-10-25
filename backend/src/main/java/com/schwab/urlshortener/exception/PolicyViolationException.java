package com.schwab.urlshortener.exception;

public class PolicyViolationException extends RuntimeException {
  public PolicyViolationException(String message) {
    super(message);
  }

  public PolicyViolationException(String policyName, String detail) {
    super("Policy violation [" + policyName + "]: " + detail);
  }
}

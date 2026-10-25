package com.schwab.urlshortener.orchestration.model;

import java.util.List;

public record ApprovalGate(
    boolean required,
    List<String> requiredApproverRoles,
    String approvalPrompt,
    boolean canAutoApprove) {
  public ApprovalGate {
    requiredApproverRoles = List.copyOf(requiredApproverRoles);
  }

  public static ApprovalGate none() {
    return new ApprovalGate(false, List.of(), "", false);
  }

  public static ApprovalGate adminRequired(String prompt) {
    return new ApprovalGate(true, List.of("ROLE_ADMIN"), prompt, false);
  }

  public static ApprovalGate autoApprove(String prompt) {
    return new ApprovalGate(true, List.of("ROLE_ADMIN"), prompt, true);
  }
}

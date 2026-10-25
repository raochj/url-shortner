package com.schwab.urlshortener.dto.request;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
    boolean approved,
    @Size(max = 1000, message = "Approval comment must not exceed 1000 characters")
        String comment) {}

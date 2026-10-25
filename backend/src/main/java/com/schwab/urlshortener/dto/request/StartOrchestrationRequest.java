package com.schwab.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record StartOrchestrationRequest(
    @NotBlank(message = "Scenario type is required")
        @Pattern(
            regexp = "(?i)GREENFIELD|BROWNFIELD|AMBIGUOUS",
            message = "Scenario type must be GREENFIELD, BROWNFIELD, or AMBIGUOUS")
        String scenarioType,
    Map<String, Object> parameters) {
  public StartOrchestrationRequest {
    if (parameters != null) parameters = Map.copyOf(parameters);
  }

  @Override
  public Map<String, Object> parameters() {
    return parameters == null ? null : Map.copyOf(parameters);
  }
}

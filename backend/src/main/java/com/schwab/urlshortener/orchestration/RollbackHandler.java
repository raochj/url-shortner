package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.model.TaskResult;

public interface RollbackHandler {
  boolean rollback(TaskResult failedResult);
}

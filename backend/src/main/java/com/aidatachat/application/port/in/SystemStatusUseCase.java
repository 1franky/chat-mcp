package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.SystemBootstrapStatus;

public interface SystemStatusUseCase {

    SystemBootstrapStatus currentStatus();
}

package com.aidatachat.web;

import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.domain.model.SystemBootstrapStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SystemStatusUseCase systemStatusUseCase;

    public SystemStatusController(SystemStatusUseCase systemStatusUseCase) {
        this.systemStatusUseCase = systemStatusUseCase;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemBootstrapStatus> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(systemStatusUseCase.currentStatus());
    }
}

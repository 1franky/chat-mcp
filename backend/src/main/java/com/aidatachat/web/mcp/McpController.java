package com.aidatachat.web.mcp;

import com.aidatachat.application.port.in.McpStatusUseCase;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpStatusUseCase mcpStatus;

    public McpController(McpStatusUseCase mcpStatus) {
        this.mcpStatus = mcpStatus;
    }

    @GetMapping("/status")
    public ResponseEntity<McpConnectionStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mcpStatus.status());
    }

    @GetMapping("/tools")
    public ResponseEntity<List<McpToolDefinition>> tools() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mcpStatus.availableTools());
    }
}

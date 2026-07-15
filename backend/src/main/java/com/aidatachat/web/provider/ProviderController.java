package com.aidatachat.web.provider;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.InvalidCredentialsException;
import com.aidatachat.application.port.in.ProviderManagementUseCase;
import com.aidatachat.application.port.in.ProviderManagementUseCase.ProviderConnectionView;
import com.aidatachat.application.port.in.ProviderManagementUseCase.ProviderModelView;
import com.aidatachat.application.port.in.ProviderManagementUseCase.ProviderTestView;
import com.aidatachat.application.port.in.ProviderManagementUseCase.SaveProviderCommand;
import com.aidatachat.domain.model.ProviderType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderManagementUseCase providers;

    public ProviderController(ProviderManagementUseCase providers) {
        this.providers = providers;
    }

    @GetMapping
    List<ProviderConnectionView> list(Authentication authentication) {
        return providers.listConnections(ownerId(authentication));
    }

    @PostMapping
    ResponseEntity<ProviderConnectionView> create(
            Authentication authentication,
            @Valid @RequestBody SaveProviderRequest body,
            HttpServletRequest request) {
        ProviderConnectionView created =
                providers.createConnection(
                        ownerId(authentication), body.toCommand(request.getRemoteAddr()));
        return ResponseEntity.created(URI.create("/api/providers/" + created.id())).body(created);
    }

    @PutMapping("/{connectionId}")
    ProviderConnectionView update(
            Authentication authentication,
            @PathVariable UUID connectionId,
            @Valid @RequestBody SaveProviderRequest body,
            HttpServletRequest request) {
        return providers.updateConnection(
                ownerId(authentication), connectionId, body.toCommand(request.getRemoteAddr()));
    }

    @DeleteMapping("/{connectionId}")
    ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID connectionId,
            HttpServletRequest request) {
        providers.deleteConnection(ownerId(authentication), connectionId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectionId}/test")
    ProviderTestView test(
            Authentication authentication,
            @PathVariable UUID connectionId,
            HttpServletRequest request) {
        return providers.testConnection(
                ownerId(authentication), connectionId, request.getRemoteAddr());
    }

    @PostMapping("/{connectionId}/models/sync")
    List<ProviderModelView> synchronizeModels(
            Authentication authentication,
            @PathVariable UUID connectionId,
            HttpServletRequest request) {
        return providers.synchronizeModels(
                ownerId(authentication), connectionId, request.getRemoteAddr());
    }

    @GetMapping("/{connectionId}/models")
    List<ProviderModelView> models(Authentication authentication, @PathVariable UUID connectionId) {
        return providers.listModels(ownerId(authentication), connectionId);
    }

    @PostMapping("/{connectionId}/models")
    ResponseEntity<ProviderModelView> addManualModel(
            Authentication authentication,
            @PathVariable UUID connectionId,
            @Valid @RequestBody ManualModelRequest body,
            HttpServletRequest request) {
        ProviderModelView model =
                providers.addManualModel(
                        ownerId(authentication),
                        connectionId,
                        body.modelId(),
                        request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(model);
    }

    @PutMapping("/{connectionId}/models/default")
    ProviderConnectionView selectDefaultModel(
            Authentication authentication,
            @PathVariable UUID connectionId,
            @Valid @RequestBody DefaultModelRequest body) {
        return providers.selectDefaultModel(ownerId(authentication), connectionId, body.modelId());
    }

    private UUID ownerId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new InvalidCredentialsException();
        }
        return principal.id();
    }

    public record SaveProviderRequest(
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotNull ProviderType providerType,
            @Size(max = 8192) String apiKey,
            @Size(max = 2048) String baseUrl,
            @Size(max = 64) String region,
            @Size(max = 255) String modelsPath,
            @Size(max = 255) String responsesPath,
            @Size(max = 255) String chatCompletionsPath,
            @Size(max = 255) String configuredModelId) {

        SaveProviderCommand toCommand(String remoteAddress) {
            return new SaveProviderCommand(
                    displayName,
                    providerType,
                    apiKey,
                    baseUrl,
                    region,
                    modelsPath,
                    responsesPath,
                    chatCompletionsPath,
                    configuredModelId,
                    remoteAddress);
        }
    }

    public record ManualModelRequest(@NotBlank @Size(max = 255) String modelId) {}

    public record DefaultModelRequest(@NotBlank @Size(max = 255) String modelId) {}
}

package com.aidatachat.web.identity;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.port.in.UserAdministrationUseCase;
import com.aidatachat.application.port.in.UserAdministrationUseCase.CreateUserCommand;
import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserPage;
import com.aidatachat.domain.model.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdministrationUseCase administration;

    public AdminUserController(UserAdministrationUseCase administration) {
        this.administration = administration;
    }

    @GetMapping
    UserPageResponse listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserPage result = administration.listUsers(page, size);
        return new UserPageResponse(
                result.users().stream().map(UserResponse::from).toList(),
                result.totalElements(),
                result.page(),
                result.size());
    }

    @PostMapping
    ResponseEntity<UserResponse> createUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateUserRequest body,
            HttpServletRequest request) {
        UserAccount created =
                administration.createUser(
                        actor.id(),
                        new CreateUserCommand(
                                body.email(),
                                body.displayName(),
                                body.password(),
                                request.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PatchMapping("/{id}/role")
    UserResponse changeRole(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest body,
            HttpServletRequest request) {
        UserAccount updated =
                administration.changeRole(actor.id(), id, body.role(), request.getRemoteAddr());
        invalidateCurrentSessionWhenTargeted(actor, id, request);
        return UserResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivateUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            HttpServletRequest request) {
        administration.deactivateUser(actor.id(), id, request.getRemoteAddr());
        invalidateCurrentSessionWhenTargeted(actor, id, request);
        return ResponseEntity.noContent().build();
    }

    private void invalidateCurrentSessionWhenTargeted(
            AuthenticatedUser actor, UUID targetId, HttpServletRequest request) {
        if (actor.id().equals(targetId) && request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record ChangeRoleRequest(@NotNull UserRole role) {}

    public record UserPageResponse(
            List<UserResponse> users, long totalElements, int page, int size) {}
}

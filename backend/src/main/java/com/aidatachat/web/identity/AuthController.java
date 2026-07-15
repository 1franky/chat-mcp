package com.aidatachat.web.identity;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.InvalidCredentialsException;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.domain.model.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityUseCase identity;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfLogoutHandler csrfLogoutHandler;

    public AuthController(
            IdentityUseCase identity,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CsrfLogoutHandler csrfLogoutHandler) {
        this.identity = identity;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfLogoutHandler = csrfLogoutHandler;
    }

    @GetMapping("/bootstrap")
    BootstrapResponse bootstrap(Authentication authentication) {
        UUID authenticatedUserId = principalId(authentication);
        IdentityUseCase.BootstrapStatus status = identity.bootstrap(authenticatedUserId);
        return new BootstrapResponse(
                status.bootstrapRequired(),
                status.registrationOpen(),
                status.authenticated(),
                status.user() == null ? null : UserResponse.from(status.user()));
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account =
                identity.register(
                        new RegisterCommand(
                                body.email(),
                                body.displayName(),
                                body.password(),
                                request.getRemoteAddr()));
        Authentication authentication =
                authenticate(body.email(), body.password(), request, response, false);
        identity.recordLoginSuccess(
                ((AuthenticatedUser) authentication.getPrincipal()).id(), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(account));
    }

    @PostMapping("/login")
    UserResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication =
                authenticate(body.email(), body.password(), request, response, true);
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        identity.recordLoginSuccess(principal.id(), request.getRemoteAddr());
        return UserResponse.from(identity.currentUser(principal.id()));
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        UUID userId = principalId(authentication);
        if (userId == null) {
            throw new InvalidCredentialsException();
        }
        return UserResponse.from(identity.currentUser(userId));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID userId = principalId(authentication);
        if (userId != null) {
            identity.recordLogout(userId, request.getRemoteAddr());
        }
        csrfLogoutHandler.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private Authentication authenticate(
            String email,
            String password,
            HttpServletRequest request,
            HttpServletResponse response,
            boolean auditFailure) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            request.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return authentication;
        } catch (AuthenticationException exception) {
            if (auditFailure) {
                identity.recordLoginFailure(request.getRemoteAddr());
            }
            throw new InvalidCredentialsException();
        }
    }

    private UUID principalId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return null;
        }
        return principal.id();
    }

    public record BootstrapResponse(
            boolean bootstrapRequired,
            boolean registrationOpen,
            boolean authenticated,
            UserResponse user) {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {}
}

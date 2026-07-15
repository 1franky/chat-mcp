package com.aidatachat.adapters.out.security;

import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.domain.model.UserAccount;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public IdentityUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
        UserAccount account =
                users.findByNormalizedEmail(normalizedEmail)
                        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new AuthenticatedUser(
                account.id(),
                account.email(),
                account.normalizedEmail(),
                account.displayName(),
                account.passwordHash(),
                account.role(),
                account.active());
    }
}

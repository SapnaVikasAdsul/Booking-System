package com.example.booking_system.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.DispatcherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityConfig.Accounts.class)
public class SecurityConfig {

    private static final String USER_CANCEL = "/bookings/{id}/cancel";
    private static final String ADMIN_CANCEL = "/admin/bookings/{id}/cancel";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        // Cancellation accepts a bodyless POST. Reject cross-origin requests before authentication.
        var cancelCors = new UrlBasedCorsConfigurationSource();
        cancelCors.registerCorsConfiguration(USER_CANCEL, new CorsConfiguration());
        cancelCors.registerCorsConfiguration(ADMIN_CANCEL, new CorsConfiguration());
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(cancelCors))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/slots", "/bookings", USER_CANCEL, ADMIN_CANCEL))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/slots").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/slots").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/bookings", USER_CANCEL).hasRole("USER")
                        .requestMatchers(HttpMethod.POST, ADMIN_CANCEL).hasRole("ADMIN")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService bookingAccounts(Accounts properties, PasswordEncoder encoder) {
        Map<String, StoredAccount> accounts = new HashMap<>();
        Set<Long> accountIds = new HashSet<>();
        properties.accounts().forEach((username, account) -> {
            if (!accountIds.add(account.id())) {
                throw new IllegalArgumentException("Booking account IDs must be unique");
            }
            if (StringUtils.hasText(account.password())) {
                accounts.put(username, new StoredAccount(account.id(), encoder.encode(account.password()), account.role()));
            }
        });

        return username -> {
            StoredAccount account = accounts.get(username);
            if (account == null) {
                throw new UsernameNotFoundException("Unknown booking account");
            }
            return new BookingPrincipal(account.id(), username, account.passwordHash(), account.role());
        };
    }

    @Validated
    @ConfigurationProperties("booking.security")
    public record Accounts(@Valid Map<String, Account> accounts) {
        public Accounts {
            accounts = accounts == null ? Map.of() : Map.copyOf(accounts);
        }
    }

    public record Account(@Positive long id, String password, @NotNull Role role) {
    }

    public enum Role {
        USER,
        ADMIN
    }

    private record StoredAccount(long id, String passwordHash, Role role) {
    }

    public static final class BookingPrincipal extends User {
        private final long id;
        private final Role role;

        private BookingPrincipal(long id, String username, String passwordHash, Role role) {
            super(username, passwordHash, AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
            this.id = id;
            this.role = role;
        }

        public long id() {
            return id;
        }

        public boolean isAdmin() {
            return role == Role.ADMIN;
        }
    }
}

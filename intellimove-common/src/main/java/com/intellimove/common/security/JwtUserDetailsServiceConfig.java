package com.intellimove.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Provides a fallback UserDetailsService when no service-specific one exists.
 * Services that have their own database-backed UserDetailsService will
 * override this automatically via @ConditionalOnMissingBean.
 */
@Configuration
public class JwtUserDetailsServiceConfig {

    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService jwtUserDetailsService() {
        return email -> User.builder()
                .username(email)
                .password("N/A")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}

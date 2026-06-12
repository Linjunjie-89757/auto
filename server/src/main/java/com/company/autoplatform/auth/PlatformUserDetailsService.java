package com.company.autoplatform.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserDetailsSupport userDetailsSupport;

    public PlatformUserDetailsService(PlatformUserDetailsSupport userDetailsSupport) {
        this.userDetailsSupport = userDetailsSupport;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDetailsSupport.loadUserByUsername(username);
    }
}

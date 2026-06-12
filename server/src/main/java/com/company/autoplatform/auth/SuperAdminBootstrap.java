package com.company.autoplatform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private final SuperAdminBootstrapSupport bootstrapSupport;

    @Value("${app.super-admin.username:superadmin}")
    private String username;

    @Value("${app.super-admin.email:superadmin@local}")
    private String email;

    @Value("${app.super-admin.display-name:Super Admin}")
    private String displayName;

    @Value("${app.super-admin.password:superadmin123}")
    private String password;

    public SuperAdminBootstrap(SuperAdminBootstrapSupport bootstrapSupport) {
        this.bootstrapSupport = bootstrapSupport;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapSupport.bootstrap(new SuperAdminProperties(username, email, displayName, password));
    }
}

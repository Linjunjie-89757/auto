package com.company.autoplatform;

import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.super-admin.password=superadmin123"
})
public abstract class IntegrationTestSupport {

    protected static final String WORKSPACE_CODE = "risk-ops";

    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final String TEST_DATABASE_URL = "jdbc:h2:mem:auto_platform_core_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static boolean databaseInitialized;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", () -> TEST_DATABASE_URL);
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    @BeforeEach
    void setCurrentUser() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                11L,
                "zhangli",
                "Zhang Li",
                "{noop}123456",
                PlatformRole.PLATFORM_ADMIN,
                1
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities())
        );
    }

    @AfterEach
    void clearCurrentUser() {
        SecurityContextHolder.clearContext();
    }

    private static synchronized void initializeDatabase() {
        if (databaseInitialized) {
            return;
        }

        Path migrationDir = Path.of("src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL, "sa", "")) {
            Files.list(migrationDir)
                    .filter(path -> path.getFileName().toString().startsWith("V"))
                    .map(MigrationScript::from)
                    .filter(MigrationScript::isVersioned)
                    .sorted(Comparator.comparingInt(MigrationScript::version))
                    .filter(script -> script.version() != 21)
                    .forEach(script -> ScriptUtils.executeSqlScript(
                            connection,
                            new EncodedResource(new FileSystemResource(script.path()), StandardCharsets.UTF_8)
                    ));
            databaseInitialized = true;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize integration test database", exception);
        }
    }

    private record MigrationScript(Path path, int version) {
        private static MigrationScript from(Path path) {
            Matcher matcher = MIGRATION_VERSION.matcher(path.getFileName().toString());
            return new MigrationScript(path, matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1);
        }

        private boolean isVersioned() {
            return version > 0;
        }
    }
}

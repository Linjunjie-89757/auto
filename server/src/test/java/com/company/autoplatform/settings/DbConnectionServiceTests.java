package com.company.autoplatform.settings;

import com.company.autoplatform.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DbConnectionServiceTests extends IntegrationTestSupport {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Test
    void dbConnectionCrudDoesNotExposePasswordAndKeepsPasswordOnBlankUpdate() {
        String connectionName = "core-db-crud-" + System.nanoTime();
        DbConnectionItem created = settingsService.createDbConnection(WORKSPACE_CODE, new DbConnectionRequest(
                null,
                connectionName,
                "H2",
                "org.h2.Driver",
                "jdbc:h2:mem:" + connectionName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "secret",
                3,
                4000,
                "created by test",
                1
        ));

        assertThat(created.id()).isNotNull();
        assertThat(created.passwordConfigured()).isTrue();
        assertThat(created.workspaceCode()).isEqualTo(WORKSPACE_CODE);

        DbConnectionEntity stored = dbConnectionMapper.selectById(created.id());
        assertThat(stored.getPasswordEncrypted()).isNotBlank();
        assertThat(stored.getPasswordEncrypted()).isNotEqualTo("secret");

        DbConnectionTestResult testResult = settingsService.testDbConnection(WORKSPACE_CODE, new DbConnectionTestRequest(
                created.id(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                3000
        ));
        assertThat(testResult.success()).isTrue();

        DbConnectionItem updated = settingsService.updateDbConnection(created.id(), WORKSPACE_CODE, new DbConnectionRequest(
                null,
                connectionName + "-updated",
                "H2",
                "org.h2.Driver",
                stored.getJdbcUrl(),
                "sa",
                "",
                5,
                6000,
                "updated by test",
                1
        ));
        assertThat(updated.connectionName()).isEqualTo(connectionName + "-updated");
        assertThat(updated.passwordConfigured()).isTrue();

        DbConnectionEntity updatedStored = dbConnectionMapper.selectById(created.id());
        assertThat(updatedStored.getPasswordEncrypted()).isEqualTo(stored.getPasswordEncrypted());

        DbConnectionItem disabled = settingsService.updateDbConnectionStatus(
                created.id(),
                WORKSPACE_CODE,
                new UpdateSettingStatusRequest(0)
        );
        assertThat(disabled.status()).isZero();

        settingsService.deleteDbConnection(created.id(), WORKSPACE_CODE);
        assertThat(dbConnectionMapper.selectById(created.id())).isNull();
    }
}

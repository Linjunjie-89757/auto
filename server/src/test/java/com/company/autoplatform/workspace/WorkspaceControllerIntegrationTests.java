package com.company.autoplatform.workspace;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.settings.CreateEnvConfigRequest;
import com.company.autoplatform.settings.EnvConfigItem;
import com.company.autoplatform.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WorkspaceControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SettingsService settingsService;

    @Test
    void listAndSwitchableKeepResponseShape() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].code", hasItem(WORKSPACE_CODE)))
                .andExpect(jsonPath("$.data[*].name").isArray())
                .andExpect(jsonPath("$.data[*].allScope", not(hasItem(true))));

        mockMvc.perform(get("/api/workspaces/switchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value(WorkspaceScope.ALL))
                .andExpect(jsonPath("$.data[0].allScope").value(true))
                .andExpect(jsonPath("$.data[*].code", hasItem(WORKSPACE_CODE)));
    }

    @Test
    void createUpdateDeleteWorkspaceKeepsResponseShape() throws Exception {
        String code = "ws_it_" + System.nanoTime();

        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "created", "PROJECT", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("created"))
                .andExpect(jsonPath("$.data.workspaceType").value("PROJECT"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.allScope").value(false));

        mockMvc.perform(put("/api/workspaces/{workspaceCode}", code)
                        .contentType("application/json")
                        .content(workspaceRequest(code, "updated", "TEAM", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("updated"))
                .andExpect(jsonPath("$.data.workspaceType").value("TEAM"));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", not(hasItem(code))));
    }

    @Test
    void deleteWorkspaceWithDependenciesFails() throws Exception {
        String code = "ws_dep_" + System.nanoTime();
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "dependency", "PROJECT", 1)))
                .andExpect(status().isOk());

        setPlatformAdminUser();
        EnvConfigItem env = settingsService.createEnv(code, new CreateEnvConfigRequest(
                null,
                "DEV",
                code + "-env",
                "https://" + code + ".example.com",
                "{}"
        ));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        setPlatformAdminUser();
        settingsService.deleteEnv(env.id(), code);
        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isOk());
    }

    @Test
    void nonPlatformAdminCannotCreateUpdateOrDeleteWorkspace() throws Exception {
        String code = "ws_deny_" + System.nanoTime();
        setMemberUser();

        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "denied", "PROJECT", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/workspaces/{workspaceCode}", WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(workspaceRequest(WORKSPACE_CODE, "denied-update", "PROJECT", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", WORKSPACE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String workspaceRequest(String code, String name, String type, int status) {
        return """
                {
                  "workspaceCode": "%s",
                  "workspaceName": "%s",
                  "description": "workspace integration test",
                  "workspaceType": "%s",
                  "status": %d
                }
                """.formatted(code, name, type, status);
    }

    private void setMemberUser() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                13L,
                "member-user",
                "Member User",
                "{noop}123456",
                PlatformRole.MEMBER,
                1
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities())
        );
    }

    private void setPlatformAdminUser() {
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
}

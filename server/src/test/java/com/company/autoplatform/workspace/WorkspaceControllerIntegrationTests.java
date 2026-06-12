package com.company.autoplatform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.settings.CreateEnvConfigRequest;
import com.company.autoplatform.settings.EnvConfigItem;
import com.company.autoplatform.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void memberCrudKeepsResponseShape() throws Exception {
        String code = "ws_mem_" + System.nanoTime();
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "members", "PROJECT", 1)))
                .andExpect(status().isOk());

        MvcResult createResult = mockMvc.perform(post("/api/workspaces/{workspaceCode}/members", code)
                        .contentType("application/json")
                        .content(memberRequest(12L, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(12))
                .andExpect(jsonPath("$.data.username").value("chennan"))
                .andExpect(jsonPath("$.data.roleCode").value("MEMBER"))
                .andReturn();
        long memberId = data(createResult).path("id").asLong();

        mockMvc.perform(get("/api/workspaces/{workspaceCode}/members", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].userId", hasItem(11)))
                .andExpect(jsonPath("$.data[*].id", hasItem(-11)))
                .andExpect(jsonPath("$.data[*].userId", hasItem(12)));

        mockMvc.perform(put("/api/workspaces/{workspaceCode}/members/{memberId}", code, memberId)
                        .contentType("application/json")
                        .content(updateMemberRequest("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(memberId))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}/members/{memberId}", code, memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/workspaces/{workspaceCode}/members", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId", not(hasItem(12))));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isOk());
    }

    @Test
    void batchMembersAndWorkspaceAccessScopeKeepBehavior() throws Exception {
        String code = "ws_scope_" + System.nanoTime();
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "scope", "PROJECT", 1)))
                .andExpect(status().isOk());

        MvcResult batchResult = mockMvc.perform(post("/api/workspaces/{workspaceCode}/members/batch", code)
                        .contentType("application/json")
                        .content(batchMemberRequest("MEMBER", 12L, 13L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].userId", hasItem(12)))
                .andExpect(jsonPath("$.data[*].userId", hasItem(13)))
                .andReturn();

        mockMvc.perform(get("/api/workspaces")
                        .with(authentication(authenticationFor(12L, "chennan", "Chen Nan", PlatformRole.MEMBER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem(code)));

        setMemberUser(12L, "chennan", "Chen Nan");
        assertThat(workspaceService.listReadableWorkspaceCodes()).contains(code);
        assertThat(workspaceService.listReadableWorkspaceIds())
                .contains(workspaceService.requireWorkspace(code).getId());
        assertThat(workspaceService.requireReadableWorkspace(code).getWorkspaceCode()).isEqualTo(code);
        assertThat(workspaceService.requireWritableWorkspace(code).getWorkspaceCode()).isEqualTo(code);

        mockMvc.perform(get("/api/workspaces/{workspaceCode}/members", code)
                        .with(authentication(authenticationFor(14L, "zhaofeng", "Zhao Feng", PlatformRole.MEMBER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        setMemberUser(14L, "zhaofeng", "Zhao Feng");
        assertThatThrownBy(() -> workspaceService.requireReadableWorkspace(code))
                .isInstanceOf(RuntimeException.class);

        setPlatformAdminUser();
        for (JsonNode item : data(batchResult)) {
            mockMvc.perform(delete("/api/workspaces/{workspaceCode}/members/{memberId}", code, item.path("id").asLong()))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isOk());
    }

    @Test
    void ownerMemberProtectionKeepsBehavior() throws Exception {
        String code = "ws_owner_" + System.nanoTime();
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content(workspaceRequest(code, "owner", "PROJECT", 1, 12L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUserId").value(12));

        MvcResult membersResult = mockMvc.perform(get("/api/workspaces/{workspaceCode}/members", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId", hasItem(12)))
                .andReturn();
        long ownerMemberId = memberIdForUser(membersResult, 12L);

        mockMvc.perform(put("/api/workspaces/{workspaceCode}/members/{memberId}", code, ownerMemberId)
                        .contentType("application/json")
                        .content(updateMemberRequest("MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}/members/{memberId}", code, ownerMemberId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/workspaces/{workspaceCode}", code)
                        .contentType("application/json")
                        .content(workspaceRequest(code, "owner", "PROJECT", 1, 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUserId").value(11));

        mockMvc.perform(delete("/api/workspaces/{workspaceCode}/members/{memberId}", code, ownerMemberId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/workspaces/{workspaceCode}", code))
                .andExpect(status().isOk());
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

    private String workspaceRequest(String code, String name, String type, int status, Long ownerUserId) {
        return """
                {
                  "workspaceCode": "%s",
                  "workspaceName": "%s",
                  "description": "workspace integration test",
                  "workspaceType": "%s",
                  "ownerUserId": %d,
                  "status": %d
                }
                """.formatted(code, name, type, ownerUserId, status);
    }

    private String memberRequest(Long userId, String roleCode) {
        return """
                {
                  "userId": %d,
                  "roleCode": "%s"
                }
                """.formatted(userId, roleCode);
    }

    private String batchMemberRequest(String roleCode, Long... userIds) {
        String ids = java.util.Arrays.stream(userIds)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                  "userIds": [%s],
                  "roleCode": "%s"
                }
                """.formatted(ids, roleCode);
    }

    private String updateMemberRequest(String roleCode) {
        return """
                {
                  "roleCode": "%s"
                }
                """.formatted(roleCode);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long memberIdForUser(MvcResult result, Long userId) throws Exception {
        for (JsonNode item : data(result)) {
            if (item.path("userId").asLong() == userId) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("Member not found for userId " + userId);
    }

    private void setMemberUser() {
        setMemberUser(13L, "liping", "Li Ping");
    }

    private void setMemberUser(Long userId, String username, String displayName) {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(userId, username, displayName, PlatformRole.MEMBER));
    }

    private Authentication authenticationFor(Long userId, String username, String displayName, String platformRole) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                userId,
                username,
                displayName,
                "{noop}123456",
                platformRole,
                1
        );
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
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

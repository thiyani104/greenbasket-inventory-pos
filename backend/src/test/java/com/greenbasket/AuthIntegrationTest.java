package com.greenbasket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import com.greenbasket.support.ApiTestBase;
import org.junit.jupiter.api.Test;

class AuthIntegrationTest extends ApiTestBase {

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void loginReturnsTokensAndMeWorks() throws Exception {
        User u = newUser(Role.MANAGER);
        JsonNode n = loginJson(u.getUsername(), PW);
        assertNotNull(n.get("accessToken"));
        assertEquals("MANAGER", n.get("user").get("role").asText());
        mvc.perform(auth(get("/api/auth/me"), n.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(u.getUsername()));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
        User u = newUser(Role.ASSISTANT);
        mvc.perform(post("/api/auth/login", "{\"username\":\"" + u.getUsername() + "\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid username or password."));
        mvc.perform(post("/api/auth/login", "{\"username\":\"ghost-user\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void accountLocksAfterFiveFailuresEvenForTheRightPassword() throws Exception {
        User u = newUser(Role.ASSISTANT);
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login", "{\"username\":\"" + u.getUsername() + "\",\"password\":\"bad\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login", "{\"username\":\"" + u.getUsername() + "\",\"password\":\"bad\"}")).andExpect(status().isLocked());
        // correct password is refused while locked
        mvc.perform(post("/api/auth/login", "{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isLocked());
        // a manager can unlock
        String mgr = tokenFor(newUser(Role.MANAGER));
        mvc.perform(auth(post("/api/users/" + u.getId() + "/unlock", "{}"), mgr)).andExpect(status().isOk());
        assertNotNull(loginJson(u.getUsername(), PW).get("accessToken"));
    }

    @Test
    void refreshRotatesAndReuseRevokesEverything() throws Exception {
        User u = newUser(Role.MANAGER);
        JsonNode first = loginJson(u.getUsername(), PW);
        String r1 = first.get("refreshToken").asText();

        JsonNode second = json.readTree(mvc.perform(post("/api/auth/refresh", "{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String r2 = second.get("refreshToken").asText();
        assertTrue(!r1.equals(r2));

        // presenting the already-rotated token is treated as theft ...
        mvc.perform(post("/api/auth/refresh", "{\"refreshToken\":\"" + r1 + "\"}")).andExpect(status().isUnauthorized());
        // ... and kills the new one as well
        mvc.perform(post("/api/auth/refresh", "{\"refreshToken\":\"" + r2 + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheRefreshToken() throws Exception {
        User u = newUser(Role.ASSISTANT);
        JsonNode n = loginJson(u.getUsername(), PW);
        String refresh = n.get("refreshToken").asText();
        mvc.perform(auth(post("/api/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}"), n.get("accessToken").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlowWorksOnceAndNeverRevealsAccounts() throws Exception {
        User u = newUser(Role.ASSISTANT);
        mvc.perform(post("/api/auth/forgot-password", "{\"usernameOrEmail\":\"nobody-here\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/forgot-password", "{\"usernameOrEmail\":\"" + u.getEmail() + "\"}")).andExpect(status().isAccepted());
        String token = notifier.lastToken.get(u.getUsername());
        assertNotNull(token);

        mvc.perform(post("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"weak\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"BrandNew123\"}")).andExpect(status().isNoContent());
        // token is single use
        mvc.perform(post("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"Another123\"}")).andExpect(status().isBadRequest());
        assertNotNull(loginJson(u.getUsername(), "BrandNew123").get("accessToken"));
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeAnythingElse() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        String name = unique("newbie");
        mvc.perform(auth(post("/api/users", "{\"username\":\"" + name + "\",\"fullName\":\"New Hire\",\"role\":\"ASSISTANT\",\"password\":\"Temp12345\"}"), mgr))
                .andExpect(status().isCreated());
        JsonNode n = loginJson(name, "Temp12345");
        String t = n.get("accessToken").asText();
        mvc.perform(auth(get("/api/products"), t)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/auth/change-password", "{\"currentPassword\":\"Temp12345\",\"newPassword\":\"Better12345\"}"), t))
                .andExpect(status().isOk());
        String t2 = loginJson(name, "Better12345").get("accessToken").asText();
        mvc.perform(auth(get("/api/products"), t2)).andExpect(status().isOk());
    }

    @Test
    void assistantsCannotUseManagerEndpoints() throws Exception {
        String ast = tokenFor(newUser(Role.ASSISTANT));
        mvc.perform(auth(get("/api/users"), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/reports/dashboard"), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/purchase-orders"), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/products", "{\"name\":\"X\",\"category\":\"Y\",\"price\":1,\"cost\":0}"), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/products"), ast)).andExpect(status().isOk());
    }
}

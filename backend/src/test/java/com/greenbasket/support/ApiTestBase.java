package com.greenbasket.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import com.greenbasket.repository.UserRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Shared helpers. Every test uses unique names so tests can share one in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class ApiTestBase {

    protected static final String PW = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected TestConfig.CapturingNotifier notifier;

    protected String unique(String prefix) { return prefix + SEQ.incrementAndGet() + "x" + (System.nanoTime() % 100000); }

    protected User newUser(Role role) {
        User u = new User(unique(role == Role.MANAGER ? "mgr" : "ast"), "Test " + role, role, encoder.encode(PW));
        u.setEmail(u.getUsername() + "@example.com");
        return users.save(u);
    }

    protected JsonNode loginJson(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected String tokenFor(User u) throws Exception {
        JsonNode n = loginJson(u.getUsername(), PW);
        return n.get("accessToken").asText();
    }

    protected static MockHttpServletRequestBuilder post(String url, String body) {
        return MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder put(String url, String body) {
        return MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    /** Creates a product as a manager and returns its id. */
    protected long createProduct(String managerToken, String name, String category, String price, String cost, int qty) throws Exception {
        String body = String.format("{\"name\":\"%s\",\"category\":\"%s\",\"price\":%s,\"cost\":%s,\"quantity\":%d,\"reorderLevel\":5}",
                name, category, price, cost, qty);
        MvcResult r = mvc.perform(auth(post("/api/products", body), managerToken)).andReturn();
        if (r.getResponse().getStatus() != 201) throw new AssertionError("create product failed: " + r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }
}

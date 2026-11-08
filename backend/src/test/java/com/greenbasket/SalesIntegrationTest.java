package com.greenbasket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.greenbasket.domain.Role;
import com.greenbasket.support.ApiTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class SalesIntegrationTest extends ApiTestBase {

    private String sale(long productId, int qty, String payment, String discount, String tendered) {
        return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":" + qty + "}],\"paymentMethod\":\"" + payment
                + "\",\"discountPercent\":" + discount + ",\"tendered\":" + tendered + "}";
    }

    private int stock(String token, long id) throws Exception {
        return json.readTree(mvc.perform(auth(get("/api/products/" + id), token)).andReturn().getResponse().getContentAsString())
                .get("quantity").asInt();
    }

    @Test
    void checkoutComputesTotalsDeductsStockAndWritesTheLedger() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("Apple"), "Fruits", "100.00", "60.00", 20);

        // 3 x 100 = 300, 10% off = 270, 5% tax on 270 = 13.50 -> total 283.50 (tax rate comes from settings)
        mvc.perform(auth(put("/api/settings", "{\"tax.rate\":\"5\"}"), mgr)).andExpect(status().isOk());
        MvcResult r = mvc.perform(auth(post("/api/sales", sale(pid, 3, "CASH", "10", "300")), mgr))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(300.00))
                .andExpect(jsonPath("$.discountAmount").value(30.00))
                .andExpect(jsonPath("$.taxAmount").value(13.50))
                .andExpect(jsonPath("$.total").value(283.50))
                .andExpect(jsonPath("$.change").value(16.50))
                .andReturn();
        mvc.perform(auth(put("/api/settings", "{\"tax.rate\":\"0\"}"), mgr)).andExpect(status().isOk());
        JsonNode sale = json.readTree(r.getResponse().getContentAsString());
        assertTrue(sale.get("receiptNo").asText().startsWith("GB-"));
        assertEquals(17, stock(mgr, pid));

        mvc.perform(auth(get("/api/products/" + pid + "/movements"), mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("SALE"))
                .andExpect(jsonPath("$.content[0].change").value(-3))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(17));
    }

    @Test
    void cannotSellMoreThanInStockOrLessThanTheTotal() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("Milk"), "Dairy", "50.00", "30.00", 2);
        mvc.perform(auth(post("/api/sales", sale(pid, 3, "CASH", "0", "500")), mgr)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/sales", sale(pid, 2, "CASH", "0", "10")), mgr)).andExpect(status().isBadRequest());
        assertEquals(2, stock(mgr, pid));                                  // nothing was deducted by the failed attempts
        mvc.perform(auth(post("/api/sales", "{\"items\":[]}"), mgr)).andExpect(status().isBadRequest());
    }

    @Test
    void assistantDiscountIsCappedAtTenPercent() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        String ast = tokenFor(newUser(Role.ASSISTANT));
        long pid = createProduct(mgr, unique("Rice"), "Grains", "100.00", "70.00", 10);
        mvc.perform(auth(post("/api/sales", sale(pid, 1, "CASH", "15", "100")), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/sales", sale(pid, 1, "CASH", "10", "100")), ast)).andExpect(status().isCreated());
    }

    @Test
    void expiredProductsCannotBeSold() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        String body = "{\"name\":\"" + unique("Yoghurt") + "\",\"category\":\"Dairy\",\"price\":40,\"cost\":20,\"quantity\":5,\"expiryDate\":\"2020-01-01\"}";
        long pid = json.readTree(mvc.perform(auth(post("/api/products", body), mgr)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(auth(post("/api/sales", sale(pid, 1, "CASH", "0", "100")), mgr))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void voidingRestoresStockAndIsManagerOnly() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        String ast = tokenFor(newUser(Role.ASSISTANT));
        long pid = createProduct(mgr, unique("Tea"), "Beverages", "200.00", "150.00", 10);
        JsonNode sale = json.readTree(mvc.perform(auth(post("/api/sales", sale(pid, 4, "CARD", "0", "0")), ast))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long saleId = sale.get("id").asLong();
        assertEquals(6, stock(mgr, pid));

        mvc.perform(auth(post("/api/sales/" + saleId + "/void", "{\"reason\":\"Customer changed mind\"}"), ast)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/sales/" + saleId + "/void", "{\"reason\":\"Customer changed mind\"}"), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        assertEquals(10, stock(mgr, pid));
        mvc.perform(auth(post("/api/sales/" + saleId + "/void", "{\"reason\":\"again\"}"), mgr)).andExpect(status().isConflict());
        assertEquals(10, stock(mgr, pid));
    }

    @Test
    void twoCashiersRacingForTheLastUnitCannotBothWin() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("LastOne"), "Misc", "10.00", "5.00", 1);
        String a = tokenFor(newUser(Role.ASSISTANT)), b = tokenFor(newUser(Role.ASSISTANT));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String t : new String[] {a, b}) {
            Callable<Integer> c = () -> {
                go.await();
                return mvc.perform(auth(post("/api/sales", sale(pid, 1, "CARD", "0", "0")), t)).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(c));
        }
        go.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) codes.add(f.get());
        pool.shutdown();

        assertEquals(1, codes.stream().filter(c -> c == 201).count(), "exactly one sale must succeed: " + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "the other must be refused: " + codes);
        assertEquals(0, stock(mgr, pid));
    }

    @Test
    void stockAdjustmentsAreValidatedAndLedgered() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("Flour"), "Grains", "80.00", "50.00", 10);
        mvc.perform(auth(post("/api/products/" + pid + "/stock", "{\"type\":\"RECEIVE\",\"quantity\":15,\"note\":\"delivery\"}"), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(25));
        mvc.perform(auth(post("/api/products/" + pid + "/stock", "{\"type\":\"WASTAGE\",\"quantity\":99,\"note\":\"spilled\"}"), mgr))
                .andExpect(status().isConflict());
        mvc.perform(auth(post("/api/products/" + pid + "/stock", "{\"type\":\"WASTAGE\",\"quantity\":5}"), mgr))
                .andExpect(status().isBadRequest());                                   // reason required
        mvc.perform(auth(post("/api/products/" + pid + "/stock", "{\"type\":\"CORRECTION\",\"quantity\":18,\"note\":\"stock take\"}"), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(18));
        mvc.perform(auth(post("/api/products/" + pid + "/stock", "{\"type\":\"SALE\",\"quantity\":1}"), mgr)).andExpect(status().isBadRequest());
    }

    @Test
    void productValidationAndSearch() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        String name = unique("Mango");
        mvc.perform(auth(post("/api/products", "{\"name\":\"\",\"category\":\"Fruits\",\"price\":10,\"cost\":5}"), mgr))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        mvc.perform(auth(post("/api/products", "{\"name\":\"" + name + "\",\"category\":\"Fruits\",\"price\":10,\"cost\":50}"), mgr))
                .andExpect(status().isBadRequest());                                   // cost above price
        long pid = createProduct(mgr, name, "Fruits", "10.00", "5.00", 100);
        mvc.perform(auth(post("/api/products", "{\"name\":\"" + name.toUpperCase() + "\",\"category\":\"Fruits\",\"price\":10,\"cost\":5}"), mgr))
                .andExpect(status().isConflict());                                     // names are unique case-insensitively
        mvc.perform(auth(get("/api/products").param("q", name.toLowerCase()), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(pid));
        mvc.perform(auth(get("/api/products").param("status", "LOW").param("q", name), mgr))
                .andExpect(jsonPath("$.totalElements").value(0));
        // archived products drop out of normal lists and cannot be sold
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/products/" + pid), mgr)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/products").param("q", name), mgr)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(post("/api/sales", sale(pid, 1, "CASH", "0", "100")), mgr)).andExpect(status().isNotFound());
    }

    @Test
    void purchaseOrderLifecycleReceivesIntoStock() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("Sugar"), "Pantry", "120.00", "90.00", 2);
        JsonNode po = json.readTree(mvc.perform(auth(post("/api/purchase-orders",
                        "{\"notes\":\"weekly\",\"items\":[{\"productId\":" + pid + ",\"quantity\":10,\"unitCost\":85}]}"), mgr))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString());
        long id = po.get("id").asLong();
        assertTrue(po.get("poNumber").asText().startsWith("PO-"));

        mvc.perform(auth(post("/api/purchase-orders/" + id + "/receive", "{}"), mgr)).andExpect(status().isConflict());   // not ordered yet
        mvc.perform(auth(post("/api/purchase-orders/" + id + "/order", "{}"), mgr)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/purchase-orders/" + id + "/receive", "{\"quantities\":{\"" + pid + "\":4}}"), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED"));       // partial delivery
        assertEquals(6, stock(mgr, pid));
        mvc.perform(auth(post("/api/purchase-orders/" + id + "/receive", "{}"), mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));      // the rest
        assertEquals(12, stock(mgr, pid));
        mvc.perform(auth(post("/api/purchase-orders/" + id + "/cancel", "{}"), mgr)).andExpect(status().isConflict());
    }

    @Test
    void reportsAddUp() throws Exception {
        String mgr = tokenFor(newUser(Role.MANAGER));
        long pid = createProduct(mgr, unique("Oats"), "Cereal", "100.00", "60.00", 50);
        mvc.perform(auth(post("/api/sales", sale(pid, 2, "CASH", "0", "200")), mgr)).andExpect(status().isCreated());
        MvcResult r = mvc.perform(auth(get("/api/reports/summary"), mgr)).andExpect(status().isOk()).andReturn();
        JsonNode s = json.readTree(r.getResponse().getContentAsString());
        assertTrue(s.get("transactions").asLong() >= 1);
        // profit = revenue - cost for the whole day, whatever else ran today
        assertEquals(0, s.get("netRevenue").decimalValue().subtract(s.get("costOfGoods").decimalValue()).compareTo(s.get("profit").decimalValue()));
        mvc.perform(auth(get("/api/reports/dashboard"), mgr)).andExpect(status().isOk()).andExpect(jsonPath("$.last7Days.length()").value(7));
    }
}

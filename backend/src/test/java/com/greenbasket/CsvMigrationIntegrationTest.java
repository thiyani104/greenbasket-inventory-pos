package com.greenbasket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.greenbasket.domain.Product;
import com.greenbasket.domain.Sale;
import com.greenbasket.domain.User;
import com.greenbasket.migration.CsvMigrationService;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.SaleRepository;
import com.greenbasket.repository.UserRepository;
import com.greenbasket.service.AuthService;
import com.greenbasket.service.SettingsService;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Own database (different URL => own Spring context), so it starts empty apart from the bootstrap admin. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:migtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CsvMigrationIntegrationTest {

    @Autowired CsvMigrationService migration;
    @Autowired ProductRepository products;
    @Autowired SaleRepository sales;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired SettingsService settings;

    @Test
    @Order(1)
    void importsSwingDataKeepingReceiptsPricesAndPasswords() throws Exception {
        Path dir = Path.of(getClass().getResource("/swing-data/users.csv").toURI()).getParent();
        CsvMigrationService.Result r = migration.importFrom(dir, true);

        assertEquals(2, r.users());
        assertEquals(2, r.products(), "free sample (price 0) and the case-insensitive duplicate are skipped");
        assertEquals(2, r.categories());
        assertEquals(2, r.suppliers());
        assertEquals(2, r.movements(), "the ledger row for a product that does not exist is skipped");
        assertEquals(2, r.sales());
        assertEquals(1, r.activity());
        assertFalse(r.warnings().isEmpty());

        Product milk = products.findByNameKey("milk, full cream 1l").orElseThrow();
        assertEquals(20, milk.getQuantity());
        assertEquals("Dairy Co", milk.getSupplier().getName());
        assertEquals(java.time.LocalDate.of(2030, 1, 1), milk.getExpiryDate());

        Sale s1 = sales.findWithItemsByReceiptNo("GB-000001").orElseThrow();
        assertEquals(2, s1.getItems().size());
        // 10x120 + 2x250 = 1700; 10% discount = 170; 5% tax on 1530 = 76.50  ->  1606.50
        assertEquals(0, new BigDecimal("1606.50").compareTo(s1.total()));
        Sale s2 = sales.findWithItemsByReceiptNo("GB-000002").orElseThrow();
        assertTrue(s2.isVoided());
        assertEquals("legacyowner", s2.getVoidedBy());

        assertEquals("Old Town Store", settings.get("store.name", ""));
        assertEquals(0, new BigDecimal("5.0").compareTo(settings.taxRate()));
    }

    @Test
    @Order(2)
    void legacyPasswordsStillWorkAndAreUpgradedToBcryptOnFirstLogin() throws Exception {
        if (users.findByUsernameKey("legacyowner").isEmpty()) {
            migration.importFrom(Path.of(getClass().getResource("/swing-data/users.csv").toURI()).getParent(), true);
        }
        User before = users.findByUsernameKey("legacyowner").orElseThrow();
        assertTrue(before.getPasswordHash().startsWith("{pbkdf2legacy}"));

        assertNotNull(auth.login("legacyowner", "Legacy@123").accessToken());

        User after = users.findByUsernameKey("legacyowner").orElseThrow();
        assertTrue(after.getPasswordHash().startsWith("{bcrypt}"), "hash should have been upgraded");
        assertNotNull(auth.login("legacyowner", "Legacy@123").accessToken());       // and still works afterwards
    }

    @Test
    @Order(3)
    void refusesToImportTwice() throws Exception {
        Path dir = Path.of(getClass().getResource("/swing-data/users.csv").toURI()).getParent();
        if (!migration.alreadyPopulated() || products.count() == 0) migration.importFrom(dir, true);
        assertThrows(IllegalStateException.class, () -> migration.importFrom(dir, true));
    }
}

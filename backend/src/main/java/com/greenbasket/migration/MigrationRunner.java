package com.greenbasket.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enabled by {@code --migrate.csv=/path/to/swing/data/folder} (or env MIGRATE_CSV). Runs before the admin bootstrap
 * so imported accounts take precedence, imports once, then lets the application start normally.
 */
@Component
@Order(0)
public class MigrationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);

    private final CsvMigrationService service;
    private final String dir;

    public MigrationRunner(CsvMigrationService service, @Value("${migrate.csv:}") String dir) {
        this.service = service;
        this.dir = dir;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dir == null || dir.isBlank()) return;
        Path p = Path.of(dir);
        if (!Files.isDirectory(p)) throw new IllegalStateException("migrate.csv is not a folder: " + dir);
        if (service.alreadyPopulated()) {
            LOG.warn("migrate.csv is set but the database already has data; skipping the import. Remove MIGRATE_CSV.");
            return;
        }
        CsvMigrationService.Result r = service.importFrom(p, false);
        LOG.info("CSV import finished: {} categories, {} suppliers, {} products, {} stock movements, {} sales, {} users, {} log rows",
                r.categories(), r.suppliers(), r.products(), r.movements(), r.sales(), r.users(), r.activity());
        r.warnings().forEach(w -> LOG.warn("import: {}", w));
    }
}

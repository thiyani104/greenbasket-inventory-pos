package com.greenbasket.service;

import com.greenbasket.domain.AppSetting;
import com.greenbasket.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    public static final Set<String> KEYS = Set.of("store.name", "store.address", "currency", "tax.rate",
            "expiry.warn.days", "default.reorder", "receipt.footer", "daily.target");

    private final AppSettingRepository repo;

    public SettingsService(AppSettingRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        for (AppSetting s : repo.findAll()) m.put(s.getKey(), s.getValue());
        return m;
    }

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return repo.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    public BigDecimal taxRate() { return decimal("tax.rate", BigDecimal.ZERO); }
    public int expiryWarnDays() { return integer("expiry.warn.days", 7); }
    public int defaultReorder() { return integer("default.reorder", 10); }
    public BigDecimal dailyTarget() { return decimal("daily.target", BigDecimal.ZERO); }
    public String currency() { return get("currency", "Rs."); }

    @Transactional
    public Map<String, String> update(Map<String, String> changes, Actor actor, ActivityService log) {
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (!KEYS.contains(e.getKey())) throw ApiException.badRequest("Unknown setting: " + e.getKey());
            String v = e.getValue() == null ? "" : e.getValue().trim();
            validate(e.getKey(), v);
            AppSetting s = repo.findById(e.getKey()).orElse(null);
            if (s == null) repo.save(new AppSetting(e.getKey(), v));
            else s.setValue(v);
        }
        log.log(actor, "Update settings", String.join(", ", changes.keySet()));
        return all();
    }

    private static void validate(String key, String v) {
        switch (key) {
            case "tax.rate" -> {
                BigDecimal d = parse(key, v);
                if (d.signum() < 0 || d.compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("Tax rate must be between 0 and 100.");
            }
            case "daily.target" -> {
                if (parse(key, v).signum() < 0) throw ApiException.badRequest("Daily target cannot be negative.");
            }
            case "expiry.warn.days", "default.reorder" -> {
                try {
                    if (Integer.parseInt(v) < 0) throw ApiException.badRequest(key + " cannot be negative.");
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest(key + " must be a whole number.");
                }
            }
            case "store.name" -> {
                if (v.isEmpty()) throw ApiException.badRequest("Store name is required.");
            }
            default -> { }
        }
        if (v.length() > 255) throw ApiException.badRequest(key + " is too long.");
    }

    private static BigDecimal parse(String key, String v) {
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(key + " must be a number.");
        }
    }

    private int integer(String key, int fallback) {
        try { return Integer.parseInt(get(key, String.valueOf(fallback)).trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private BigDecimal decimal(String key, BigDecimal fallback) {
        try { return new BigDecimal(get(key, fallback.toPlainString()).trim()); } catch (NumberFormatException e) { return fallback; }
    }
}

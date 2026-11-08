package com.greenbasket.web;

import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.ActivityService;
import com.greenbasket.service.SettingsService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Settings & activity")
public class SettingsController {

    private final SettingsService settings;
    private final ActivityService activity;

    public SettingsController(SettingsService settings, ActivityService activity) {
        this.settings = settings;
        this.activity = activity;
    }

    @GetMapping("/settings")
    public Map<String, String> get() { return settings.all(); }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('MANAGER')")
    public Map<String, String> update(@RequestBody Map<String, String> changes) {
        return settings.update(changes, CurrentUser.get().actor(), activity);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasRole('MANAGER')")
    public PageDto<ActivityDto> activity(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        Page<com.greenbasket.domain.ActivityLog> p = activity.search(q, page, size);
        return PageDto.of(p, ActivityDto::of);
    }
}

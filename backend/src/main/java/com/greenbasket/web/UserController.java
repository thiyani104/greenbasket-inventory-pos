package com.greenbasket.web;

import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.UserService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @GetMapping
    public List<UserDto> list() { return users.list().stream().map(UserDto::of).toList(); }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest r) {
        var u = users.create(r.username(), r.fullName(), r.email(), r.role(), r.password(), CurrentUser.get().actor());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.of(u));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        return UserDto.of(users.update(id, r.fullName(), r.email(), r.role(), r.active(), CurrentUser.get().actor()));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable Long id, @Valid @RequestBody TempPasswordRequest r) {
        users.resetPassword(id, r.temporaryPassword(), CurrentUser.get().actor());
    }

    @PostMapping("/{id}/unlock")
    public UserDto unlock(@PathVariable Long id) { return UserDto.of(users.unlock(id, CurrentUser.get().actor())); }
}

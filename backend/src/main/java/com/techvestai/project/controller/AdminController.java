package com.techvestai.project.controller;

import com.techvestai.project.entity.User;
import com.techvestai.project.enums.UserRole;
import com.techvestai.project.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin user-management controller — Task 13.11.
 *
 * <p>GET  /api/v1/admin/users — ADMIN: list all users.<br>
 * POST /api/v1/admin/users — ADMIN: create a new user.
 *
 * <p><b>Requirements:</b> 1.6
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /** GET /api/v1/admin/users — returns all registered users. */
    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    /**
     * POST /api/v1/admin/users — creates a new user.
     *
     * <p>Accepts a simple JSON body with {@code username}, {@code password},
     * and {@code role} fields. In a production system this would use a dedicated
     * request DTO with validation; kept minimal here to stay within task scope.
     */
    @PostMapping
    public ResponseEntity<Void> createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam UserRole role) {

        userService.createUser(username, password, role);
        return ResponseEntity.status(201).build();
    }
}

package com.placepulse.auth.web;

import com.placepulse.auth.dto.AuthDtos.UserResponse;
import com.placepulse.auth.security.AuthenticatedUser;
import com.placepulse.auth.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(principal.id());
    }

    /** Placeholder TPR-only endpoint, proves role-based access works. Real TPR APIs come later. */
    @GetMapping("/tpr/ping")
    public Map<String, String> tprPing() {
        return Map.of("status", "ok");
    }
}

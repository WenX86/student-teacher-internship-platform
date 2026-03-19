package com.internship.platform.controller;

import com.internship.platform.common.ApiResponse;
import com.internship.platform.dto.Requests;
import com.internship.platform.service.PhaseOneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PhaseOneService phaseOneService;

    public AuthController(PhaseOneService phaseOneService) {
        this.phaseOneService = phaseOneService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody Requests.LoginRequest request) {
        return ApiResponse.ok(phaseOneService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(phaseOneService.currentUserPayload(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody Requests.ChangePasswordRequest request) {
        phaseOneService.changePassword(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/login-records")
    public ApiResponse<List<Map<String, Object>>> loginRecords() {
        return ApiResponse.ok(phaseOneService.loginRecords(phaseOneService.currentLoginUser()));
    }
}

package com.placepulse.drive.web;

import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.UpdateStageRequest;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.service.DriveService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final DriveService service;

    public ApplicationController(DriveService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    public List<ApplicationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myApplications(user);
    }

    @PatchMapping("/{id}/stage")
    public ApplicationResponse updateStage(@PathVariable UUID id, @Valid @RequestBody UpdateStageRequest request) {
        return service.updateStage(id, request.stage());
    }
}

package com.placepulse.drive.web;

import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.BulkStageRequest;
import com.placepulse.drive.dto.DriveDtos.UpdateStageRequest;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.service.DriveService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /** Deletes the application so that student can apply again. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        service.removeApplication(id);
    }

    /** Moves several applications to one stage at once. */
    @PatchMapping("/stage")
    public List<ApplicationResponse> bulkStage(@Valid @RequestBody BulkStageRequest request) {
        return service.bulkUpdateStage(request.ids(), request.stage());
    }

    @PatchMapping("/{id}/stage")
    public ApplicationResponse updateStage(@PathVariable UUID id, @Valid @RequestBody UpdateStageRequest request) {
        return service.updateStage(id, request.stage());
    }
}

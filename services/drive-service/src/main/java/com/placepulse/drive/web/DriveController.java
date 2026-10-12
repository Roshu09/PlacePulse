package com.placepulse.drive.web;

import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.CreateDriveRequest;
import com.placepulse.drive.dto.DriveDtos.DriveResponse;
import com.placepulse.drive.dto.DriveDtos.UpdateStatusRequest;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.service.DriveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drives")
public class DriveController {

    private final DriveService service;

    public DriveController(DriveService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriveResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateDriveRequest request) {
        return service.create(user, request);
    }

    @GetMapping
    public List<DriveResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user);
    }

    @GetMapping("/{id}")
    public DriveResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @PatchMapping("/{id}/status")
    public DriveResponse setStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.setStatus(id, request.status());
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@PathVariable UUID id,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.apply(id, user, authorization);
    }

    @GetMapping("/{id}/applications")
    public List<ApplicationResponse> applicants(@PathVariable UUID id) {
        return service.applicants(id);
    }
}

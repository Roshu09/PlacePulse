package com.placepulse.drive.web;

import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.CreateDriveRequest;
import com.placepulse.drive.dto.DriveDtos.DriveResponse;
import com.placepulse.drive.dto.DriveDtos.UpdateStatusRequest;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.service.DriveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drives")
public class DriveController {

    private final DriveService service;

    public DriveController(DriveService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriveResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateDriveRequest request) {
        return service.create(user, request, null);
    }

    /** Same as above, plus an optional PNG logo: a "drive" JSON part and a "logo" file part. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriveResponse createWithLogo(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestPart("drive") CreateDriveRequest request,
                                        @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return service.create(user, request, logo);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DriveResponse update(@PathVariable UUID id, @Valid @RequestBody CreateDriveRequest request) {
        return service.update(id, request, null);
    }

    /** Edit with an optional new logo ("logo" part), or set removeLogo in the JSON to delete the current one. */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DriveResponse updateWithLogo(@PathVariable UUID id,
                                        @Valid @RequestPart("drive") CreateDriveRequest request,
                                        @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return service.update(id, request, logo);
    }

    /** Public on purpose: a logo is shown in an <img> tag, which cannot send a login token. */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // the URL carries a version, so a changed logo gets a new URL and this can be cached hard
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(service.logo(id));
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

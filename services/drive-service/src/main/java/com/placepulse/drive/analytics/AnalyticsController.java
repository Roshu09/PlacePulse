package com.placepulse.drive.analytics;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /**
     * TPR only (see SecurityConfig). The X- headers let you measure the cache from curl or k6, and
     * ?nocache=true computes straight from the database without touching Redis (used by the benchmark).
     */
    @GetMapping
    public ResponseEntity<String> analytics(@RequestParam(defaultValue = "false") boolean nocache) {
        AnalyticsService.Result r = service.get(nocache);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Cache", r.cacheHit() ? "HIT" : "MISS")
                .header("X-Compute-Ms", String.valueOf(r.millis()))
                .body(r.json());
    }
}

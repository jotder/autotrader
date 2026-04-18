package com.rj.web;

import com.rj.config.OptionChainConfig;
import com.rj.engine.options.OptionChainService;
import com.rj.engine.options.OptionChainSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/option-chain")
public class OptionChainController {

    private final OptionChainService optionChainService;
    private final OptionChainConfig config;

    public OptionChainController(OptionChainService optionChainService,
                                 OptionChainConfig config) {
        this.optionChainService = optionChainService;
        this.config = config;
    }

    /**
     * Full chain for one underlying — all expiries.
     * Optional {@code ?expiry=DD-MMM-YYYY} filters the {@code expiryData} list.
     */
    @GetMapping("/{underlying}")
    public ResponseEntity<?> getChain(
            @PathVariable String underlying,
            @RequestParam(required = false) String expiry) {

        return optionChainService.getChain(underlying)
                .map(snap -> ResponseEntity.ok(toResponse(snap, expiry)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Manual refresh trigger — returns 202 Accepted immediately. */
    @PostMapping("/{underlying}/refresh")
    public ResponseEntity<?> refresh(@PathVariable String underlying) {
        optionChainService.refresh(underlying);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Refresh triggered for " + underlying,
                "underlying", underlying));
    }

    /** Summary row per tracked underlying: PCR, VIX LTP, fetchedAt, stale flag. */
    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        return optionChainService.allSnapshots().entrySet().stream()
                .map(e -> {
                    OptionChainSnapshot snap = e.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("underlying", snap.underlying());
                    row.put("pcr", snap.pcr());
                    row.put("vixLtp", snap.data().indiaVix != null ? snap.data().indiaVix.ltp : null);
                    row.put("fetchedAt", snap.fetchedAt().toString());
                    row.put("stale", snap.isStale(staleThreshold));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> toResponse(OptionChainSnapshot snap, String expiryFilter) {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        var expiries = snap.data().expiryData;
        if (expiryFilter != null && !expiryFilter.isBlank() && expiries != null) {
            expiries = expiries.stream()
                    .filter(e -> expiryFilter.equals(e.date))
                    .toList();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("underlying", snap.underlying());
        result.put("fetchedAt", snap.fetchedAt().toString());
        result.put("stale", snap.isStale(staleThreshold));
        result.put("pcr", snap.pcr());
        result.put("vixLtp", snap.data().indiaVix != null ? snap.data().indiaVix.ltp : null);
        result.put("expiryData", expiries);
        result.put("optionsChain", snap.data().optionsChain);
        return result;
    }
}

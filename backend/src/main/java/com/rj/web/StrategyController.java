package com.rj.web;

import com.rj.config.ConfigValidator;
import com.rj.config.StrategyService;
import com.rj.config.StrategyVersionInfo;
import com.rj.config.StrategyYamlConfig;
import com.rj.web.dto.ActionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller for strategy configuration, versioning, and lifecycle management.
 */
@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    public List<StrategyVersionInfo> list() {
        return strategyService.getAllStrategies();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StrategyVersionInfo> get(@PathVariable String id) {
        StrategyVersionInfo info = strategyService.getStrategy(id);
        return info != null ? ResponseEntity.ok(info) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/draft")
    public ActionResponse createDraft(@PathVariable String id) {
        strategyService.createDraft(id);
        return new ActionResponse(true, "Draft created for strategy: " + id);
    }

    @PutMapping("/{id}/draft")
    public ActionResponse updateDraft(@PathVariable String id, @RequestBody StrategyYamlConfig config) {
        strategyService.updateDraft(id, config);
        return new ActionResponse(true, "Draft updated for strategy: " + id);
    }

    @PostMapping("/{id}/promote")
    public ActionResponse promoteDraft(@PathVariable String id) {
        try {
            strategyService.promoteDraft(id);
            return new ActionResponse(true, "Draft promoted to active for strategy: " + id);
        } catch (Exception e) {
            return new ActionResponse(false, "Failed to promote draft: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/toggle")
    public ActionResponse toggle(@PathVariable String id) {
        try {
            strategyService.toggleStrategy(id);
            return new ActionResponse(true, "Strategy toggle successful for: " + id);
        } catch (IOException e) {
            return new ActionResponse(false, "Failed to toggle strategy: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/duplicate")
    public ActionResponse duplicate(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newId = body.get("newId");
        if (newId == null || newId.isBlank()) {
            return new ActionResponse(false, "newId is required");
        }
        try {
            strategyService.duplicateStrategy(id, newId);
            return new ActionResponse(true, "Strategy duplicated from " + id + " to " + newId);
        } catch (IOException e) {
            return new ActionResponse(false, "Failed to duplicate strategy: " + e.getMessage());
        }
    }

    @GetMapping("/defaults")
    public StrategyYamlConfig defaults() {
        return strategyService.getDefaults();
    }

    @PostMapping("/validate")
    public ConfigValidator.ValidationResult validate(@RequestBody StrategyYamlConfig config) {
        return strategyService.validate(config);
    }
}

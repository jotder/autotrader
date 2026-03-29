package com.rj.web;

import com.rj.model.TradeSignal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for viewing generated alpha-signals and their historical performance.
 */
@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final Path signalLogPath = Path.of("data/journal/signals.csv");

    @GetMapping
    public List<String> getSignals(@RequestParam(required = false) String symbol,
                                   @RequestParam(defaultValue = "100") int limit) {
        if (!Files.exists(signalLogPath)) return List.of();

        try (Stream<String> lines = Files.lines(signalLogPath)) {
            return lines.skip(1) // Skip header
                    .filter(line -> symbol == null || line.contains(symbol))
                    .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                        Collections.reverse(list);
                        return list.stream().limit(limit).collect(Collectors.toList());
                    }));
        } catch (IOException e) {
            return List.of("Error reading signals: " + e.getMessage());
        }
    }
}

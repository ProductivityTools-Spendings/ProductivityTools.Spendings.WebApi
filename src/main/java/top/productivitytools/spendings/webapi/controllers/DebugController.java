package top.productivitytools.spendings.webapi.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/hello")
    public String Hello() {
        return "Hello";
    }

    @GetMapping("/appName")
    public String AppName() {
        return "PTSpendings";
    }

    @GetMapping("/date")
    public String Date() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    @GetMapping({"/serverName", "/ServerName"})
    public String ServerName() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(NULLIF(current_setting('cluster_name', true), ''), current_database())",
                String.class
        );
    }
}

package top.productivitytools.familyexpenses.webapi.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @GetMapping("/hello")
    public String Hello() {
        return "Hello";
    }

    @GetMapping("/appName")
    public String AppName() {
        return "PTFamilyExpenses";
    }

    @GetMapping("/date")
    public String Date() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    @GetMapping({"/serverName", "/ServerName"})
    public String ServerName() {
        return "No database";
    }
}

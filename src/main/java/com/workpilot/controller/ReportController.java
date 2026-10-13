package com.workpilot.controller;

import com.workpilot.dto.ReportDtos;
import com.workpilot.service.ReportService;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserContextService userContextService;

    @GetMapping
    public ReportDtos.ReportListResponse list() {
        return reportService.list(userContextService.currentUserId());
    }

    @GetMapping("/{runId}")
    public ReportDtos.ReportDetail get(@PathVariable Long runId) {
        return reportService.get(userContextService.currentUserId(), runId);
    }
}

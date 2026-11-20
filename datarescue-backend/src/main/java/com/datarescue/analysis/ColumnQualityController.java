package com.datarescue.analysis;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
@CrossOrigin(origins = "http://localhost:4200")
public class ColumnQualityController {

    private final ColumnQualityService columnQualityService;

    public ColumnQualityController(
            ColumnQualityService columnQualityService) {

        this.columnQualityService =
                columnQualityService;
    }

    @GetMapping("/columns/{datasetId}")
    public List<ColumnQualityResult> analyzeColumns(
            @PathVariable Long datasetId) {

        return columnQualityService
                .analyzeColumns(datasetId);
    }
}
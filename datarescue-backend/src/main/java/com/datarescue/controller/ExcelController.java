package com.datarescue.controller;

import com.datarescue.entity.Dataset;
import com.datarescue.excel.ExcelReaderService;
import com.datarescue.repository.DatasetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import com.datarescue.excel.ExcelPreviewService;

@RestController
@RequestMapping("/api/excel")
@CrossOrigin(origins = "http://localhost:4200")
public class ExcelController {

    private final ExcelReaderService excelReaderService;
    private final DatasetRepository datasetRepository;

                public ExcelController(
                        ExcelReaderService excelReaderService,
                        ExcelPreviewService excelPreviewService,
                        DatasetRepository datasetRepository) {

                this.excelReaderService = excelReaderService;
                this.excelPreviewService = excelPreviewService;
                this.datasetRepository = datasetRepository;
                }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeExcel(
            @RequestParam("file") MultipartFile file) {

        try {

            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please upload an Excel file.");
            }

            String fileName = file.getOriginalFilename();

            if (fileName == null ||
                    (!fileName.toLowerCase().endsWith(".xlsx")
                            && !fileName.toLowerCase().endsWith(".xls"))) {

                return ResponseEntity.badRequest()
                        .body("Only Excel files (.xlsx or .xls) are supported.");
            }

            ExcelReaderService.ExcelAnalysisResult result =
                    excelReaderService.analyzeExcel(file);

           double qualityScore = calculateQualityScore(
                    result.getTotalRows(),
                    result.getTotalColumns(),
                    result.getMissingValues(),
                    result.getDuplicateRows()
            );

            Dataset dataset = new Dataset();

            dataset.setFileName(result.getFileName());
            dataset.setFileType(file.getContentType());
            dataset.setFileSize(file.getSize());
            dataset.setFileData(file.getBytes());
            dataset.setTotalRows(result.getTotalRows());
            dataset.setTotalColumns(result.getTotalColumns());
            dataset.setMissingValues(result.getMissingValues());
            dataset.setDuplicateRows(result.getDuplicateRows());
            dataset.setQualityScore(qualityScore);
            dataset.setUploadedAt(LocalDateTime.now());

            Dataset savedDataset = datasetRepository.save(dataset);

            return ResponseEntity.ok(savedDataset);

        } catch (IOException e) {

            return ResponseEntity.internalServerError()
                    .body("Failed to read Excel file: " + e.getMessage());
        }
    }

    @GetMapping("/preview/{id}")
public ResponseEntity<?> previewExcel(
        @PathVariable Long id) {

    try {

        ExcelPreviewService.PreviewResult result =
                excelPreviewService.previewDataset(id);

        return ResponseEntity.ok(result);

    } catch (RuntimeException e) {

        return ResponseEntity.badRequest()
                .body(e.getMessage());

    } catch (IOException e) {

        return ResponseEntity.internalServerError()
                .body("Failed to preview Excel file: " + e.getMessage());
    }
}

    private double calculateQualityScore(
            int totalRows,
            int totalColumns,
            int missingValues,
            int duplicateRows) {

        if (totalRows <= 0 || totalColumns <= 0) {
            return 0.0;
        }

        int totalCells = totalRows * totalColumns;

        double missingPercentage =
                ((double) missingValues / totalCells) * 100;

        double duplicatePercentage =
                ((double) duplicateRows / totalRows) * 100;

        double score =
                100.0
                - (missingPercentage * 0.7)
                - (duplicatePercentage * 0.3);

        return Math.max(0.0, Math.min(100.0, score));
    }

    private final ExcelPreviewService excelPreviewService;


}
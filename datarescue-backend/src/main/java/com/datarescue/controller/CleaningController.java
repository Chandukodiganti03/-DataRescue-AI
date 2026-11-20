
package com.datarescue.controller;

import com.datarescue.cleaning.DatasetCleaningService;
import com.datarescue.entity.Dataset;
import com.datarescue.repository.DatasetRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.*;

@RestController
@RequestMapping("/api/clean")
@CrossOrigin(origins = "http://localhost:4200")
public class CleaningController {

    private final DatasetCleaningService cleaningService;
    private final DatasetRepository datasetRepository;

    public CleaningController(
            DatasetCleaningService cleaningService,
            DatasetRepository datasetRepository) {
        this.cleaningService = cleaningService;
        this.datasetRepository = datasetRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> cleanDataset(@PathVariable Long id) {
        try {
            DatasetCleaningService.CleaningResult result =
                    cleaningService.cleanDataset(id);

            String originalName = result.getFileName();
            String cleanedName = "cleaned_dataset.xlsx";

            if (originalName != null &&
                    originalName.toLowerCase().endsWith(".xlsx")) {
                cleanedName = originalName.substring(
                        0, originalName.length() - 5
                ) + "_cleaned.xlsx";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ));
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(cleanedName)
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(result.getCleanedFile());

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to clean dataset: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<?> getCleaningReport(@PathVariable Long id) {
        try {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException("Dataset not found with id: " + id));

            DatasetCleaningService.CleaningResult result =
                    cleaningService.cleanDataset(id);

            QualityAnalysis analysis = analyzeOriginalFile(dataset);

            String qualityStatus =
                    getQualityStatus(result.getCleanedQualityScore());

            String qualityExplanation =
                    getQualityExplanation(
                            result,
                            analysis
                    );

            CleaningReport report = new CleaningReport(
                    result.getFileName(),
                    result.getOriginalRows(),
                    result.getCleanedRows(),
                    result.getOriginalColumns(),
                    result.getCleanedColumns(),
                    result.getRemovedColumns(),
                    result.getRepeatedHeaderRows(),
                    result.getDuplicateRows(),
                    result.getOriginalQualityScore(),
                    result.getCleanedQualityScore(),
                    result.getQualityImprovement(),
                    qualityStatus,
                    qualityExplanation,
                    analysis.missingValues,
                    analysis.duplicateRecords,
                    analysis.emptyColumns,
                    analysis.columnsWithMissingValues,
                    analysis.recommendations
            );

            return ResponseEntity.ok(report);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to generate cleaning report: " + e.getMessage());
        }
    }

    private QualityAnalysis analyzeOriginalFile(Dataset dataset)
            throws Exception {

        if (dataset.getFileData() == null ||
                dataset.getFileData().length == 0) {
            throw new RuntimeException(
                    "Original Excel file is not available for analysis."
            );
        }

        QualityAnalysis analysis = new QualityAnalysis();

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(dataset.getFileData());
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                return analysis;
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int headerIndex = findHeaderRow(sheet, formatter);

            if (headerIndex < 0) {
                analysis.recommendations.add(
                        "A reliable header row could not be identified. Check the first rows of the worksheet."
                );
                return analysis;
            }

            Row header = sheet.getRow(headerIndex);

            if (header == null) {
                return analysis;
            }

            int columnCount = Math.max(header.getLastCellNum(), 0);
            List<String> columnNames = new ArrayList<>();
            List<Integer> missingByColumn = new ArrayList<>();
            List<Boolean> columnHasData = new ArrayList<>();

            for (int col = 0; col < columnCount; col++) {
                String name = formatter.formatCellValue(
                        header.getCell(col)
                ).trim();

                if (name.isEmpty()) {
                    name = "Column " + (col + 1);
                }

                columnNames.add(name);
                missingByColumn.add(0);
                columnHasData.add(false);
            }

            Set<List<String>> seenRows = new HashSet<>();

            for (int rowIndex = headerIndex + 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                List<String> rowValues = new ArrayList<>();
                boolean rowHasData = false;

                for (int col = 0; col < columnCount; col++) {
                    Cell cell = row.getCell(col);

                    String value =
                            formatter.formatCellValue(cell).trim();

                    rowValues.add(value);

                    if (!value.isEmpty()) {
                        rowHasData = true;
                        columnHasData.set(col, true);
                    }
                }

                // Ignore completely blank rows in all quality counts.
                if (!rowHasData) {
                    continue;
                }

                for (int col = 0; col < columnCount; col++) {
                    if (rowValues.get(col).isEmpty()) {
                        missingByColumn.set(
                                col,
                                missingByColumn.get(col) + 1
                        );
                        analysis.missingValues++;
                    }
                }

                if (!seenRows.add(rowValues)) {
                    analysis.duplicateRecords++;
                }
            }

            for (int col = 0; col < columnCount; col++) {
                int missing = missingByColumn.get(col);

                if (missing > 0) {
                    double percentage = analysis.dataRows == 0
                            ? 0.0
                            : (missing * 100.0 / analysis.dataRows);

                    analysis.columnsWithMissingValues.add(
                            new ColumnIssue(
                                    columnNames.get(col),
                                    missing,
                                    Math.round(percentage * 100.0) / 100.0
                            )
                    );
                }

                if (!columnHasData.get(col)) {
                    analysis.emptyColumns++;
                }
            }

            // Derive the number of non-empty records for percentage calculations.
            analysis.dataRows = countNonEmptyDataRows(
                    sheet, headerIndex, columnCount, formatter
            );

            // Recalculate percentages now that dataRows is known.
            for (ColumnIssue issue : analysis.columnsWithMissingValues) {
                double percentage = analysis.dataRows == 0
                        ? 0.0
                        : issue.getMissingCount() * 100.0 / analysis.dataRows;

                issue.setMissingPercentage(
                        Math.round(percentage * 100.0) / 100.0
                );
            }

            buildRecommendations(analysis);
        }

        return analysis;
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int bestRow = -1;
        int highestTextCount = 0;

        for (int rowIndex = 0;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            int textCount = 0;
            int nonEmptyCount = 0;

            for (Cell cell : row) {
                String value = formatter.formatCellValue(cell).trim();

                if (value.isEmpty()) {
                    continue;
                }

                nonEmptyCount++;

                if (cell.getCellType() == CellType.STRING) {
                    textCount++;
                }
            }

            if (nonEmptyCount >= 3 &&
                    textCount >= 3 &&
                    textCount > highestTextCount) {
                highestTextCount = textCount;
                bestRow = rowIndex;
            }
        }

        if (bestRow >= 0) {
            return bestRow;
        }

        for (int rowIndex = 0;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            for (Cell cell : row) {
                if (!formatter.formatCellValue(cell).trim().isEmpty()) {
                    return rowIndex;
                }
            }
        }

        return -1;
    }

    private int countNonEmptyDataRows(
            Sheet sheet,
            int headerIndex,
            int columnCount,
            DataFormatter formatter) {

        int count = 0;

        for (int rowIndex = headerIndex + 1;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            boolean hasData = false;

            for (int col = 0; col < columnCount; col++) {
                if (!formatter.formatCellValue(row.getCell(col))
                        .trim().isEmpty()) {
                    hasData = true;
                    break;
                }
            }

            if (hasData) {
                count++;
            }
        }

        return count;
    }

    private void buildRecommendations(QualityAnalysis analysis) {
        if (analysis.missingValues > 0) {
            analysis.recommendations.add(
                    "Review missing values in the listed columns. Fill them only when a reliable value is available; otherwise consider an explicit Unknown value or an appropriate analysis strategy."
            );
        }

        if (analysis.duplicateRecords > 0) {
            analysis.recommendations.add(
                    "Review duplicate records and confirm whether they represent repeated entries or legitimate transactions before removing them."
            );
        }

        if (analysis.emptyColumns > 0) {
            analysis.recommendations.add(
                    "Remove completely empty columns if they are not required for future data collection."
            );
        }

        if (analysis.recommendations.isEmpty()) {
            analysis.recommendations.add(
                    "No missing values, duplicate records, or completely empty columns were detected by these checks. Validate business rules and data formats before relying on the dataset."
            );
        }
    }

    private String getQualityStatus(double score) {
        if (score >= 90.0) return "Excellent";
        if (score >= 75.0) return "Good";
        if (score >= 50.0) return "Needs Improvement";
        return "Poor";
    }

    private String getQualityExplanation(
            DatasetCleaningService.CleaningResult result,
            QualityAnalysis analysis) {

        List<String> issues = new ArrayList<>();

        if (analysis.missingValues > 0) {
            issues.add(analysis.missingValues + " missing values");
        }

        if (analysis.duplicateRecords > 0) {
            issues.add(analysis.duplicateRecords + " duplicate records");
        }

        if (analysis.emptyColumns > 0) {
            issues.add(analysis.emptyColumns + " completely empty columns");
        }

        if (issues.isEmpty()) {
            return "The checks found no missing values, duplicate records, or completely empty columns. Further validation may still be needed for business rules and inconsistent formats.";
        }

        return "The original dataset contains " +
                String.join(", ", issues) +
                ". Review the detailed findings and recommendations before using this data for analysis.";
    }

    public static class CleaningReport {
        private final String fileName;
        private final int originalRows;
        private final int cleanedRows;
        private final int originalColumns;
        private final int cleanedColumns;
        private final int removedColumns;
        private final int repeatedHeaderRows;
        private final int duplicateRows;
        private final double originalQualityScore;
        private final double cleanedQualityScore;
        private final double qualityImprovement;
        private final String qualityStatus;
        private final String qualityExplanation;
        private final int missingValues;
        private final int duplicateRecordsDetected;
        private final int emptyColumnsDetected;
        private final List<ColumnIssue> columnsWithMissingValues;
        private final List<String> recommendations;

        public CleaningReport(
                String fileName,
                int originalRows,
                int cleanedRows,
                int originalColumns,
                int cleanedColumns,
                int removedColumns,
                int repeatedHeaderRows,
                int duplicateRows,
                double originalQualityScore,
                double cleanedQualityScore,
                double qualityImprovement,
                String qualityStatus,
                String qualityExplanation,
                int missingValues,
                int duplicateRecordsDetected,
                int emptyColumnsDetected,
                List<ColumnIssue> columnsWithMissingValues,
                List<String> recommendations) {

            this.fileName = fileName;
            this.originalRows = originalRows;
            this.cleanedRows = cleanedRows;
            this.originalColumns = originalColumns;
            this.cleanedColumns = cleanedColumns;
            this.removedColumns = removedColumns;
            this.repeatedHeaderRows = repeatedHeaderRows;
            this.duplicateRows = duplicateRows;
            this.originalQualityScore = originalQualityScore;
            this.cleanedQualityScore = cleanedQualityScore;
            this.qualityImprovement = qualityImprovement;
            this.qualityStatus = qualityStatus;
            this.qualityExplanation = qualityExplanation;
            this.missingValues = missingValues;
            this.duplicateRecordsDetected = duplicateRecordsDetected;
            this.emptyColumnsDetected = emptyColumnsDetected;
            this.columnsWithMissingValues = columnsWithMissingValues;
            this.recommendations = recommendations;
        }

        public String getFileName() { return fileName; }
        public int getOriginalRows() { return originalRows; }
        public int getCleanedRows() { return cleanedRows; }
        public int getOriginalColumns() { return originalColumns; }
        public int getCleanedColumns() { return cleanedColumns; }
        public int getRemovedColumns() { return removedColumns; }
        public int getRepeatedHeaderRows() { return repeatedHeaderRows; }
        public int getDuplicateRows() { return duplicateRows; }
        public double getOriginalQualityScore() { return originalQualityScore; }
        public double getCleanedQualityScore() { return cleanedQualityScore; }
        public double getQualityImprovement() { return qualityImprovement; }
        public String getQualityStatus() { return qualityStatus; }
        public String getQualityExplanation() { return qualityExplanation; }
        public int getMissingValues() { return missingValues; }
        public int getDuplicateRecordsDetected() { return duplicateRecordsDetected; }
        public int getEmptyColumnsDetected() { return emptyColumnsDetected; }

        public List<ColumnIssue> getColumnsWithMissingValues() {
            return columnsWithMissingValues;
        }

        public List<String> getRecommendations() {
            return recommendations;
        }
    }

    public static class ColumnIssue {
        private final String columnName;
        private final int missingCount;
        private double missingPercentage;

        public ColumnIssue(
                String columnName,
                int missingCount,
                double missingPercentage) {
            this.columnName = columnName;
            this.missingCount = missingCount;
            this.missingPercentage = missingPercentage;
        }

        public String getColumnName() { return columnName; }
        public int getMissingCount() { return missingCount; }
        public double getMissingPercentage() { return missingPercentage; }

        public void setMissingPercentage(double missingPercentage) {
            this.missingPercentage = missingPercentage;
        }
    }

    private static class QualityAnalysis {
        private int dataRows;
        private int missingValues;
        private int duplicateRecords;
        private int emptyColumns;
        private final List<ColumnIssue> columnsWithMissingValues =
                new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();
    }
}
package com.datarescue.analysis;

import com.datarescue.entity.Dataset;
import com.datarescue.repository.DatasetRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ColumnQualityService {

    private final DatasetRepository datasetRepository;

    public ColumnQualityService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    public List<ColumnQualityResult> analyzeColumns(Long datasetId) {

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() ->
                        new RuntimeException("Dataset not found"));

        if (dataset.getFileData() == null ||
                dataset.getFileData().length == 0) {

            throw new RuntimeException(
                    "Dataset file data is not available");
        }

        try (
                Workbook workbook =
                        WorkbookFactory.create(
                                new ByteArrayInputStream(
                                        dataset.getFileData()))
        ) {

            Sheet sheet = workbook.getSheetAt(0);

            int headerRowIndex = findHeaderRow(sheet);

            if (headerRowIndex == -1) {
                throw new RuntimeException(
                        "Unable to detect dataset header");
            }

            Row headerRow =
                    sheet.getRow(headerRowIndex);

            int columnCount =
                    getActualColumnCount(headerRow);

            List<ColumnQualityResult> results =
                    new ArrayList<>();

            for (int columnIndex = 0;
                 columnIndex < columnCount;
                 columnIndex++) {

                String columnName =
                        getCellValue(
                                headerRow.getCell(columnIndex))
                                .trim();

                if (columnName.isEmpty()) {
                    columnName =
                            "Column " + (columnIndex + 1);
                }

                int totalValues = 0;
                int missingValues = 0;

                for (int rowIndex =
                             headerRowIndex + 1;
                     rowIndex <= sheet.getLastRowNum();
                     rowIndex++) {

                    Row row =
                            sheet.getRow(rowIndex);

                    if (row == null) {
                        continue;
                    }

                    boolean completelyEmptyRow =
                            isCompletelyEmptyRow(
                                    row,
                                    columnCount);

                    if (completelyEmptyRow) {
                        continue;
                    }

                    totalValues++;

                    Cell cell =
                            row.getCell(columnIndex);

                    String value =
                            getCellValue(cell).trim();

                    if (value.isEmpty()) {
                        missingValues++;
                    }
                }

                double qualityScore = 100.0;

                if (totalValues > 0) {

                    double missingPercentage =
                            ((double) missingValues
                                    / totalValues) * 100.0;

                    qualityScore =
                            100.0 - missingPercentage;
                }

                String status =
                        determineStatus(qualityScore);

                results.add(
                        new ColumnQualityResult(
                                columnName,
                                totalValues,
                                missingValues,
                                round(qualityScore),
                                status
                        )
                );
            }

            return results;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Unable to analyze column quality",
                    exception);
        }
    }


    private String determineStatus(double score) {

        if (score >= 95.0) {
            return "Excellent";
        }

        if (score >= 80.0) {
            return "Good";
        }

        if (score >= 60.0) {
            return "Needs Improvement";
        }

        return "Poor";
    }


    private double round(double value) {

        return Math.round(value * 100.0) / 100.0;
    }


    private int findHeaderRow(Sheet sheet) {

        int bestHeaderRow = -1;
        double bestScore =
                Double.NEGATIVE_INFINITY;

        int lastRow =
                sheet.getLastRowNum();

        for (int rowIndex = 0;
             rowIndex <= lastRow;
             rowIndex++) {

            Row row =
                    sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            int nonEmptyCells = 0;
            int textCells = 0;
            int numericCells = 0;

            for (Cell cell : row) {

                String value =
                        getCellValue(cell).trim();

                if (value.isEmpty()) {
                    continue;
                }

                nonEmptyCells++;

                if (cell.getCellType()
                        == CellType.STRING) {

                    textCells++;

                } else if (
                        cell.getCellType()
                                == CellType.NUMERIC) {

                    numericCells++;
                }
            }

            if (nonEmptyCells < 3 ||
                    textCells < 3) {

                continue;
            }

            double textRatio =
                    (double) textCells
                            / nonEmptyCells;

            double score =
                    (nonEmptyCells * 2.0)
                    + (textCells * 3.0)
                    + (textRatio * 5.0)
                    - (numericCells * 2.0);

            score -= rowIndex * 0.75;

            if (score > bestScore) {

                bestScore = score;
                bestHeaderRow = rowIndex;
            }
        }

        if (bestHeaderRow != -1) {
            return bestHeaderRow;
        }

        for (int rowIndex = 0;
             rowIndex <= lastRow;
             rowIndex++) {

            Row row =
                    sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            for (Cell cell : row) {

                if (!getCellValue(cell)
                        .trim()
                        .isEmpty()) {

                    return rowIndex;
                }
            }
        }

        return -1;
    }


    private int getActualColumnCount(Row row) {

        if (row == null) {
            return 0;
        }

        short lastCell =
                row.getLastCellNum();

        return Math.max(lastCell, 0);
    }


    private boolean isCompletelyEmptyRow(
            Row row,
            int columnCount) {

        for (int columnIndex = 0;
             columnIndex < columnCount;
             columnIndex++) {

            Cell cell =
                    row.getCell(columnIndex);

            if (!getCellValue(cell)
                    .trim()
                    .isEmpty()) {

                return false;
            }
        }

        return true;
    }


    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter =
                new DataFormatter();

        return formatter.formatCellValue(cell);
    }
}
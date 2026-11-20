package com.datarescue.excel;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class ExcelReaderService {

    public ExcelAnalysisResult analyzeExcel(MultipartFile file) throws IOException {

        List<String> columnNames = new ArrayList<>();
        Set<String> uniqueRows = new HashSet<>();

        int totalRows = 0;
        int totalColumns = 0;
        int missingValues = 0;
        int duplicateRows = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            /*
             * Detect the real dataset header.
             *
             * This handles messy Excel files where the first
             * few rows may contain titles, descriptions,
             * merged cells, or other non-table information.
             */
            int headerRowIndex = findHeaderRow(sheet);

            if (headerRowIndex == -1) {
                return new ExcelAnalysisResult(
                        file.getOriginalFilename(),
                        0,
                        0,
                        0,
                        0,
                        columnNames
                );
            }

            Row headerRow = sheet.getRow(headerRowIndex);

            /*
             * Determine the real number of columns
             * from the detected header row.
             */
            totalColumns = getActualColumnCount(headerRow);

            /*
             * Read column names.
             */
            for (int columnIndex = 0;
                 columnIndex < totalColumns;
                 columnIndex++) {

                Cell cell = headerRow.getCell(columnIndex);

                String columnName =
                        getCellValue(cell).trim();

                if (columnName.isEmpty()) {
                    columnName =
                            "Column " + (columnIndex + 1);
                }

                columnNames.add(columnName);
            }

            /*
             * Analyze data rows after the header.
             */
            for (int rowIndex = headerRowIndex + 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                StringBuilder rowData =
                        new StringBuilder();

                boolean rowHasData = false;

                int rowMissingValues = 0;

                for (int columnIndex = 0;
                     columnIndex < totalColumns;
                     columnIndex++) {

                    Cell cell =
                            row.getCell(columnIndex);

                    String value =
                            getCellValue(cell).trim();

                    if (!value.isEmpty()) {

                        rowHasData = true;

                    } else {

                        rowMissingValues++;
                    }

                    rowData
                            .append(value)
                            .append("|");
                }

                /*
                 * Ignore completely empty rows.
                 *
                 * Missing values are counted only for
                 * actual data rows.
                 */
                if (!rowHasData) {
                    continue;
                }

                totalRows++;

                missingValues += rowMissingValues;

                String rowKey =
                        rowData.toString();

                if (!uniqueRows.add(rowKey)) {
                    duplicateRows++;
                }
            }
        }

        return new ExcelAnalysisResult(
                file.getOriginalFilename(),
                totalRows,
                totalColumns,
                missingValues,
                duplicateRows,
                columnNames
        );
    }


    /*
     * Detect the most likely real table header.
     *
     * This is intentionally the same logic used by
     * ExcelPreviewService and DatasetCleaningService.
     */
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

            Set<String> values =
                    new HashSet<>();

            for (Cell cell : row) {

                String value =
                        getCellValue(cell).trim();

                if (value.isEmpty()) {
                    continue;
                }

                nonEmptyCells++;

                values.add(
                        value.toLowerCase()
                );

                if (cell.getCellType()
                        == CellType.STRING) {

                    textCells++;

                } else if (cell.getCellType()
                        == CellType.NUMERIC) {

                    numericCells++;
                }
            }

            /*
             * A useful header should contain
             * multiple text values.
             */
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

            /*
             * Prefer headers that appear earlier
             * in the worksheet.
             */
            double positionPenalty =
                    rowIndex * 0.75;

            score -= positionPenalty;

            /*
             * Give extra weight to common
             * column-header words.
             */
            for (String value : values) {

                if (value.contains("date")
                        || value.contains("name")
                        || value.equals("id")
                        || value.contains("amount")
                        || value.contains("value")
                        || value.contains("total")
                        || value.contains("description")
                        || value.contains("remarks")
                        || value.contains("narration")
                        || value.contains("quantity")
                        || value.contains("price")
                        || value.contains("address")
                        || value.contains("gst")
                        || value.contains("tax")
                        || value.contains("invoice")
                        || value.contains("number")
                        || value.contains("no.")) {

                    score += 2.0;
                }
            }

            if (score > bestScore) {

                bestScore = score;

                bestHeaderRow = rowIndex;
            }
        }

        /*
         * Fallback:
         * if no strong header is found,
         * use the first row containing data.
         */
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

        if (lastCell < 0) {
            return 0;
        }

        return lastCell;
    }


    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter =
                new DataFormatter();

        return formatter.formatCellValue(cell);
    }


    public static class ExcelAnalysisResult {

        private final String fileName;

        private final int totalRows;

        private final int totalColumns;

        private final int missingValues;

        private final int duplicateRows;

        private final List<String> columnNames;


        public ExcelAnalysisResult(
                String fileName,
                int totalRows,
                int totalColumns,
                int missingValues,
                int duplicateRows,
                List<String> columnNames) {

            this.fileName = fileName;

            this.totalRows = totalRows;

            this.totalColumns = totalColumns;

            this.missingValues = missingValues;

            this.duplicateRows = duplicateRows;

            this.columnNames = columnNames;
        }


        public String getFileName() {
            return fileName;
        }


        public int getTotalRows() {
            return totalRows;
        }


        public int getTotalColumns() {
            return totalColumns;
        }


        public int getMissingValues() {
            return missingValues;
        }


        public int getDuplicateRows() {
            return duplicateRows;
        }


        public List<String> getColumnNames() {
            return columnNames;
        }
    }
}
package com.datarescue.excel;

import com.datarescue.entity.Dataset;
import com.datarescue.repository.DatasetRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExcelPreviewService {

    private final DatasetRepository datasetRepository;

    public ExcelPreviewService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    public PreviewResult previewDataset(Long id) throws IOException {

        Dataset dataset =
                datasetRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Dataset not found with id: " + id
                                )
                        );

        if (dataset.getFileData() == null ||
                dataset.getFileData().length == 0) {

            throw new RuntimeException(
                    "Original Excel file is not available for this dataset."
            );
        }

        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(
                                dataset.getFileData()
                        );

                Workbook workbook =
                        WorkbookFactory.create(inputStream)
        ) {

            Sheet sheet = workbook.getSheetAt(0);

            int headerRowIndex = findHeaderRow(sheet);

            if (headerRowIndex == -1) {

                return new PreviewResult(
                        dataset.getFileName(),
                        columns,
                        rows
                );
            }

            Row headerRow = sheet.getRow(headerRowIndex);

            int totalColumns =
                    getActualColumnCount(headerRow);

            for (int columnIndex = 0;
                 columnIndex < totalColumns;
                 columnIndex++) {

                Cell cell =
                        headerRow.getCell(columnIndex);

                String columnName =
                        getCellValue(cell).trim();

                if (columnName.isEmpty()) {

                    columnName =
                            "Column " +
                                    (columnIndex + 1);
                }

                columns.add(columnName);
            }

            int previewRowLimit = 20;

            for (int rowIndex =
                         headerRowIndex + 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                if (rows.size() >= previewRowLimit) {
                    break;
                }

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                List<String> rowData =
                        new ArrayList<>();

                boolean hasData = false;

                for (int columnIndex = 0;
                     columnIndex < totalColumns;
                     columnIndex++) {

                    Cell cell =
                            row.getCell(columnIndex);

                    String value =
                            getCellValue(cell).trim();

                    if (!value.isEmpty()) {
                        hasData = true;
                    }

                    rowData.add(value);
                }

                if (hasData) {
                    rows.add(rowData);
                }
            }
        }

        return new PreviewResult(
                dataset.getFileName(),
                columns,
                rows
        );
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

            if (nonEmptyCells < 3 ||
                    textCells < 3) {

                continue;
            }

            double textRatio =
                    (double) textCells /
                            nonEmptyCells;

            double score =
                    (nonEmptyCells * 2.0)
                            + (textCells * 3.0)
                            + (textRatio * 5.0)
                            - (numericCells * 2.0);

            /*
             * Prefer headers near the beginning
             * of the worksheet.
             */
            double positionPenalty =
                    rowIndex * 0.75;

            score -= positionPenalty;

            /*
             * Give additional weight to common
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
                        || value.contains("s.no")
                        || value.equals("no")) {

                    score += 2.0;
                }
            }

            if (score > bestScore) {

                bestScore = score;
                bestHeaderRow = rowIndex;
            }
        }

        /*
         * Fallback to the first non-empty row.
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

        return Math.max(lastCell, 0);
    }

    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter =
                new DataFormatter();

        return formatter.formatCellValue(cell);
    }

    public static class PreviewResult {

        private final String fileName;

        private final List<String> columns;

        private final List<List<String>> rows;

        public PreviewResult(
                String fileName,
                List<String> columns,
                List<List<String>> rows) {

            this.fileName = fileName;
            this.columns = columns;
            this.rows = rows;
        }

        public String getFileName() {
            return fileName;
        }

        public List<String> getColumns() {
            return columns;
        }

        public List<List<String>> getRows() {
            return rows;
        }
    }
}

package com.datarescue.cleaning;

import com.datarescue.entity.Dataset;
import com.datarescue.repository.DatasetRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class DatasetCleaningService {

    private final DatasetRepository datasetRepository;

    public DatasetCleaningService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    public CleaningResult cleanDataset(Long datasetId) throws IOException {

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new RuntimeException(
                        "Dataset not found with id: " + datasetId));

        byte[] originalFile = dataset.getFileData();

        if (originalFile == null || originalFile.length == 0) {
            throw new RuntimeException(
                    "Original Excel file is not available for this dataset.");
        }

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(originalFile);
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException(
                        "The Excel workbook contains no worksheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);

            int headerRowIndex = findHeaderRow(sheet);

            if (headerRowIndex < 0) {
                throw new RuntimeException(
                        "Could not find a valid header row.");
            }



            Set<Integer> summaryRows = new HashSet<>();

for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
    Row candidate = sheet.getRow(r);

    if (candidate == null) {
        continue;
    }

    int sumFormulaCount = 0;

    for (Cell cell : candidate) {
        if (cell.getCellType() == CellType.FORMULA
                && cell.getCellFormula()
                       .toUpperCase(Locale.ROOT)
                       .replace(" ", "")
                       .contains("SUM(")) {
            sumFormulaCount++;
        }
    }

    if (sumFormulaCount >= 2) {
        summaryRows.add(r);
    }
}

for (Integer r : summaryRows) {
    Row summaryRow = sheet.getRow(r);

    if (summaryRow != null) {
        sheet.removeRow(summaryRow);
    }
}

            int originalRows =
                    countDataRows(sheet, headerRowIndex);

            int originalColumns =
                    getActualColumnCount(sheet.getRow(headerRowIndex));

            int originalMissingValues =
                    countMissingValues(
                            sheet, headerRowIndex, originalColumns);

            int originalDuplicateCount =
                    countDuplicateRows(
                            sheet, headerRowIndex, originalColumns);

            double originalQualityScore =
                    calculateQualityScore(
                            originalRows,
                            originalColumns,
                            originalMissingValues,
                            originalDuplicateCount);

            freezeFormulaResults(workbook);
            
            int remainingFormulaCount = 0;
            int formulaErrorCount = 0;

            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        remainingFormulaCount++;
                    } else if (cell.getCellType() == CellType.ERROR) {
                        formulaErrorCount++;
                    }
                }
            }

            System.out.println("DEBUG - Formulas after freezing: "
                    + remainingFormulaCount);
            System.out.println("DEBUG - Error cells after freezing: "
                    + formulaErrorCount);


            List<Integer> validColumns =
                    findValidColumns(
                            sheet, headerRowIndex, originalColumns);

            int removedColumns =
                    originalColumns - validColumns.size();

            removeEmptyRows(
                    sheet, headerRowIndex, originalColumns);

            compactColumns(sheet, validColumns, originalColumns);

            int cleanedColumns = validColumns.size();

            trimCellValues(sheet);

            int repeatedHeaderRows =
                    removeRepeatedHeaderRows(
                            sheet, headerRowIndex, cleanedColumns);

            int duplicateRows =
                    removeDuplicateRows(
                            sheet, headerRowIndex, cleanedColumns);

            int cleanedRows =
                    countDataRows(sheet, headerRowIndex);

            int cleanedMissingValues =
                    countMissingValues(
                            sheet, headerRowIndex, cleanedColumns);

            int remainingDuplicates =
                    countDuplicateRows(
                            sheet, headerRowIndex, cleanedColumns);

            double cleanedQualityScore =
                    calculateQualityScore(
                            cleanedRows,
                            cleanedColumns,
                            cleanedMissingValues,
                            remainingDuplicates);

            originalQualityScore =
                    applyRepeatedHeaderPenalty(
                            originalQualityScore,
                            originalRows,
                            repeatedHeaderRows);

            double qualityImprovement =
                    cleanedQualityScore - originalQualityScore;

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            workbook.write(output);

            return new CleaningResult(
                    dataset.getFileName(),
                    output.toByteArray(),
                    originalRows,
                    cleanedRows,
                    originalColumns,
                    cleanedColumns,
                    removedColumns,
                    repeatedHeaderRows,
                    duplicateRows,
                    originalQualityScore,
                    cleanedQualityScore,
                    qualityImprovement);
        }
    }

    private void freezeFormulaResults(Workbook workbook) {

        FormulaEvaluator evaluator =
                workbook.getCreationHelper().createFormulaEvaluator();

        Map<Cell, CellValue> results = new IdentityHashMap<>();

        Set<Cell> unresolvedFormulas =
        Collections.newSetFromMap(new IdentityHashMap<>());

        // First evaluate formulas while all original formulas are intact.
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.FORMULA) {
                        continue;
                    }

                    try {
                        CellValue result = evaluator.evaluate(cell);

                        if (result != null && result.getCellType() != CellType.ERROR) {
                            results.put(cell, result);
                        } else {
                            unresolvedFormulas.add(cell);
                        }

                    } catch (RuntimeException ex) {
                        unresolvedFormulas.add(cell);

                        System.err.println(
                                "Could not evaluate formula at "
                                        + cell.getAddress()
                                        + ": " + ex.getMessage());
                    }
                }
            }
        }

        // Only after evaluation is complete, replace formulas with results.
        for (Map.Entry<Cell, CellValue> entry : results.entrySet()) {
            Cell cell = entry.getKey();
            CellValue result = entry.getValue();

                try {
                    cell.setCellFormula(null);

                    switch (result.getCellType()) {
                    case STRING:
                        cell.setCellValue(result.getStringValue());
                        break;

                    case NUMERIC:
                        cell.setCellValue(result.getNumberValue());
                        break;

                    case BOOLEAN:
                        cell.setCellValue(result.getBooleanValue());
                        break;

                    case ERROR:
                        cell.setCellErrorValue(result.getErrorValue());
                        break;

                    case BLANK:
                        cell.setBlank();
                        break;

                    default:
                    unresolvedFormulas.add(cell);
                    break;
                }
            } catch (RuntimeException ex) {
                System.err.println(
                        "Could not save formula result at "
                                + cell.getAddress()
                                + ": " + ex.getMessage());
            }
        }


        System.out.println("DEBUG - Unresolved formulas: "
        + unresolvedFormulas.size());

        for (Cell cell : unresolvedFormulas) {
            if (cell.getCellType() == CellType.FORMULA) {
                String formula = cell.getCellFormula();

                cell.setCellFormula(null);
                cell.setCellValue(
                        "[Formula not evaluated: =" + formula + "]");
            }
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

        long totalCells = (long) totalRows * totalColumns;

        double missingPercentage =
                totalCells == 0 ? 0.0 :
                ((double) missingValues / totalCells) * 100.0;

        double duplicatePercentage =
                ((double) duplicateRows / totalRows) * 100.0;

        double score = 100.0
                - (missingPercentage * 0.7)
                - (duplicatePercentage * 0.3);

        return Math.max(0.0, Math.min(100.0, score));
    }

    private int countMissingValues(
            Sheet sheet,
            int headerRowIndex,
            int totalColumns) {

        int missingValues = 0;

        for (int rowIndex = headerRowIndex + 1;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            boolean rowHasData = false;
            int rowMissingValues = 0;

            for (int col = 0; col < totalColumns; col++) {
                String value = getCellValue(row.getCell(col)).trim();

                if (value.isEmpty()) {
                    rowMissingValues++;
                } else {
                    rowHasData = true;
                }
            }

            if (rowHasData) {
                missingValues += rowMissingValues;
            }
        }

        return missingValues;
    }

    private int countDuplicateRows(
            Sheet sheet,
            int headerRowIndex,
            int totalColumns) {

        Set<String> seenRows = new HashSet<>();
        int duplicates = 0;

        for (int rowIndex = headerRowIndex + 1;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String key = createRowKey(row, totalColumns);

            if (key.isBlank()) {
                continue;
            }

            if (!seenRows.add(key)) {
                duplicates++;
            }
        }

        return duplicates;
    }

    private int findHeaderRow(Sheet sheet) {

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
                String value = getCellValue(cell).trim();

                if (value.isEmpty()) {
                    continue;
                }

                nonEmptyCount++;

                if (cell.getCellType() == CellType.STRING) {
                    textCount++;
                }
            }

            if (nonEmptyCount >= 3
                    && textCount >= 3
                    && textCount > highestTextCount) {

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
                if (!getCellValue(cell).trim().isEmpty()) {
                    return rowIndex;
                }
            }
        }

        return -1;
    }

    private int getActualColumnCount(Row row) {
        return row == null ? 0 : Math.max(row.getLastCellNum(), 0);
    }

    private List<Integer> findValidColumns(
            Sheet sheet,
            int headerRowIndex,
            int totalColumns) {

        List<Integer> validColumns = new ArrayList<>();

        for (int col = 0; col < totalColumns; col++) {

            boolean hasData = false;

            for (int rowIndex = headerRowIndex + 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                if (!getCellValue(row.getCell(col)).trim().isEmpty()) {
                    hasData = true;
                    break;
                }
            }

            if (hasData) {
                validColumns.add(col);
            }
        }

        if (validColumns.isEmpty()) {
            Row header = sheet.getRow(headerRowIndex);

            if (header != null) {
                for (int col = 0; col < totalColumns; col++) {
                    if (!getCellValue(header.getCell(col)).trim().isEmpty()) {
                        validColumns.add(col);
                    }
                }
            }
        }

        return validColumns;
    }

    private void compactColumns(
            Sheet sheet,
            List<Integer> validColumns,
            int originalColumnCount) {

        for (int rowIndex = 0;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            List<CellSnapshot> snapshots = new ArrayList<>();

            for (Integer oldColumn : validColumns) {
                snapshots.add(new CellSnapshot(row.getCell(oldColumn)));
            }

            for (int col = 0; col < originalColumnCount; col++) {
                Cell cell = row.getCell(col);

                if (cell != null) {
                    row.removeCell(cell);
                }
            }

            for (int col = 0; col < snapshots.size(); col++) {
                CellSnapshot snapshot = snapshots.get(col);

                if (snapshot.type == null) {
                    continue;
                }

                Cell target = row.createCell(col, snapshot.type);
                snapshot.restoreTo(target);
            }
        }
    }

    private void removeEmptyRows(
            Sheet sheet,
            int headerRowIndex,
            int totalColumns) {

        for (int rowIndex = sheet.getLastRowNum();
             rowIndex > headerRowIndex;
             rowIndex--) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            boolean hasData = false;

            for (int col = 0; col < totalColumns; col++) {
                if (!getCellValue(row.getCell(col)).trim().isEmpty()) {
                    hasData = true;
                    break;
                }
            }

            if (!hasData) {
                sheet.removeRow(row);
            }
        }
    }

    private void trimCellValues(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    cell.setCellValue(cell.getStringCellValue().trim());
                }
            }
        }
    }

    private int removeRepeatedHeaderRows(
            Sheet sheet,
            int headerRowIndex,
            int columnCount) {

        Row header = sheet.getRow(headerRowIndex);

        if (header == null || columnCount == 0) {
            return 0;
        }

        List<String> headerValues = new ArrayList<>();

        for (int col = 0; col < columnCount; col++) {
            headerValues.add(
                    getCellValue(header.getCell(col))
                            .trim()
                            .toLowerCase(Locale.ROOT));
        }

        int removed = 0;

        for (int rowIndex = sheet.getLastRowNum();
             rowIndex > headerRowIndex;
             rowIndex--) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            int nonEmpty = 0;
            int textCells = 0;
            int matching = 0;

            for (int col = 0; col < columnCount; col++) {
                Cell cell = row.getCell(col);

                String value = getCellValue(cell)
                        .trim()
                        .toLowerCase(Locale.ROOT);

                if (value.isEmpty()) {
                    continue;
                }

                nonEmpty++;

                if (cell != null && cell.getCellType() == CellType.STRING) {
                    textCells++;
                }

                if (!headerValues.get(col).isEmpty()
                        && value.equals(headerValues.get(col))) {
                    matching++;
                }
            }

            double matchRatio = (double) matching / columnCount;

            if (nonEmpty >= 3
                    && textCells >= 3
                    && matching >= 3
                    && matchRatio >= 0.60) {

                sheet.removeRow(row);
                removed++;
            }
        }

        return removed;
    }

    private int removeDuplicateRows(
            Sheet sheet,
            int headerRowIndex,
            int columnCount) {

        Set<String> seenRows = new HashSet<>();
        int removed = 0;

        for (int rowIndex = sheet.getLastRowNum();
             rowIndex > headerRowIndex;
             rowIndex--) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String key = createRowKey(row, columnCount);

            if (key.isBlank()) {
                continue;
            }

            if (!seenRows.add(key)) {
                sheet.removeRow(row);
                removed++;
            }
        }

        return removed;
    }

    private String createRowKey(Row row, int columnCount) {
        StringBuilder builder = new StringBuilder();

        for (int col = 0; col < columnCount; col++) {
            builder.append(getCellValue(row.getCell(col)).trim())
                    .append('\u001F');
        }

        return builder.toString();
    }

    private int countDataRows(Sheet sheet, int headerRowIndex) {

        int count = 0;

        for (int rowIndex = headerRowIndex + 1;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            boolean hasData = false;

            for (Cell cell : row) {
                if (!getCellValue(cell).trim().isEmpty()) {
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

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        return new DataFormatter().formatCellValue(cell);
    }

    private double applyRepeatedHeaderPenalty(
            double qualityScore,
            int totalRows,
            int repeatedHeaderRows) {

        if (totalRows <= 0 || repeatedHeaderRows <= 0) {
            return qualityScore;
        }

        double percentage =
                ((double) repeatedHeaderRows / totalRows) * 100.0;

        double penalty = percentage * 0.5;

        return Math.max(0.0, Math.min(100.0, qualityScore - penalty));
    }

    private static class CellSnapshot {

        private final CellType type;
        private final CellStyle style;
        private final String stringValue;
        private final double numericValue;
        private final boolean booleanValue;
        private final byte errorValue;
        private final String formula;

        private CellSnapshot(Cell cell) {
            if (cell == null) {
                type = null;
                style = null;
                stringValue = null;
                numericValue = 0;
                booleanValue = false;
                errorValue = 0;
                formula = null;
                return;
            }

            type = cell.getCellType();
            style = cell.getCellStyle();

            stringValue = type == CellType.STRING
                    ? cell.getStringCellValue() : null;

            numericValue = type == CellType.NUMERIC
                    ? cell.getNumericCellValue() : 0;

            booleanValue = type == CellType.BOOLEAN
                    && cell.getBooleanCellValue();

            errorValue = type == CellType.ERROR
                    ? cell.getErrorCellValue() : 0;

            formula = type == CellType.FORMULA
                    ? cell.getCellFormula() : null;
        }

        private void restoreTo(Cell cell) {
            if (style != null) {
                cell.setCellStyle(style);
            }

            switch (type) {
                case STRING:
                    cell.setCellValue(stringValue);
                    break;

                case NUMERIC:
                    cell.setCellValue(numericValue);
                    break;

                case BOOLEAN:
                    cell.setCellValue(booleanValue);
                    break;

                case FORMULA:
                    cell.setCellFormula(formula);
                    break;

                case ERROR:
                    cell.setCellErrorValue(errorValue);
                    break;

                case BLANK:
                default:
                    cell.setBlank();
                    break;
            }
        }
    }

    public static class CleaningResult {

        private final String fileName;
        private final byte[] cleanedFile;
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

        public CleaningResult(
                String fileName,
                byte[] cleanedFile,
                int originalRows,
                int cleanedRows,
                int originalColumns,
                int cleanedColumns,
                int removedColumns,
                int repeatedHeaderRows,
                int duplicateRows,
                double originalQualityScore,
                double cleanedQualityScore,
                double qualityImprovement) {

            this.fileName = fileName;
            this.cleanedFile = cleanedFile;
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
        }

        public String getFileName() { return fileName; }
        public byte[] getCleanedFile() { return cleanedFile; }
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
    }
}

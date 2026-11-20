package com.datarescue.analysis;

public class ColumnQualityResult {

    private String columnName;
    private int totalValues;
    private int missingValues;
    private double qualityScore;
    private String status;

    public ColumnQualityResult() {
    }

    public ColumnQualityResult(
            String columnName,
            int totalValues,
            int missingValues,
            double qualityScore,
            String status) {

        this.columnName = columnName;
        this.totalValues = totalValues;
        this.missingValues = missingValues;
        this.qualityScore = qualityScore;
        this.status = status;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public int getTotalValues() {
        return totalValues;
    }

    public void setTotalValues(int totalValues) {
        this.totalValues = totalValues;
    }

    public int getMissingValues() {
        return missingValues;
    }

    public void setMissingValues(int missingValues) {
        this.missingValues = missingValues;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
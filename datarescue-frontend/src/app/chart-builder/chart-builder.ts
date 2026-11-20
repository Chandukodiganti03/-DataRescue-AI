import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  Dataset,
  DatasetService
} from '../services/dataset.service';

type ChartMetric =
  | 'qualityScore'
  | 'missingValues'
  | 'duplicateRows'
  | 'totalRows'
  | 'totalColumns';

interface ChartBar {
  label: string;
  value: number;
  width: number;
}

interface RecentChart {
  id: string;
  title: string;
  metric: ChartMetric;
  createdAt: string;
  bars: ChartBar[];
}

@Component({
  selector: 'app-chart-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './chart-builder.html',
  styleUrl: './chart-builder.css'
})
export class ChartBuilder implements OnInit {

  datasets: Dataset[] = [];
  loading = true;
  errorMessage = '';

  selectedMetric: ChartMetric = 'qualityScore';

  metricOptions: { value: ChartMetric; label: string }[] = [
    { value: 'qualityScore', label: 'Data Quality Score' },
    { value: 'missingValues', label: 'Missing Values' },
    { value: 'duplicateRows', label: 'Duplicate Rows' },
    { value: 'totalRows', label: 'Total Rows' },
    { value: 'totalColumns', label: 'Total Columns' }
  ];

  recentCharts: RecentChart[] = [];
  activeChart: RecentChart | null = null;

  constructor(private datasetService: DatasetService) {}

  ngOnInit(): void {
    this.loadRecentCharts();
    this.loadDatasets();
  }

  loadDatasets(): void {
    this.loading = true;

    this.datasetService.getDatasets().subscribe({
      next: (response: Dataset[]) => {
        this.datasets = [...response].sort(
          (a, b) => b.id - a.id
        );
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load datasets:', error);
        this.errorMessage =
          'Unable to load datasets. Please check the backend.';
        this.loading = false;
      }
    });
  }

  createChart(): void {
    if (this.datasets.length === 0) {
      this.errorMessage = 'Upload a dataset before creating a chart.';
      return;
    }

    const metric = this.selectedMetric;
    const option = this.metricOptions.find(
      item => item.value === metric
    );

    const values = this.datasets.map(dataset => {
      const rawValue = dataset[metric];
      const value = Number(rawValue);

      return {
        label: `${dataset.fileName} (ID: ${dataset.id})`,
        value: Number.isFinite(value) ? Math.max(0, value) : 0
      };
    });

    const maxValue = metric === 'qualityScore'
      ? 100
      : Math.max(1, ...values.map(item => item.value));

    const chart: RecentChart = {
      id: `${Date.now()}`,
      title: `${option?.label ?? 'Dataset Statistics'} by Dataset`,
      metric,
      createdAt: new Date().toLocaleString(),
      bars: values.map(item => ({
        label: item.label,
        value: item.value,
        width: Math.min(100, (item.value / maxValue) * 100)
      }))
    };

    this.recentCharts = [
      chart,
      ...this.recentCharts.filter(item => item.id !== chart.id)
    ].slice(0, 5);

    this.activeChart = chart;
    this.errorMessage = '';
    this.saveRecentCharts();
  }

  selectChart(chart: RecentChart): void {
    this.activeChart = chart;
  }

  private loadRecentCharts(): void {
    try {
      const saved = localStorage.getItem(
        'datarescueChatRecentCharts'
      );

      if (saved) {
        this.recentCharts = JSON.parse(saved) as RecentChart[];
        this.activeChart = this.recentCharts[0] ?? null;
      }
    } catch (error) {
      console.error('Unable to load recent charts:', error);
      this.recentCharts = [];
      this.activeChart = null;
    }
  }

  private saveRecentCharts(): void {
    try {
      localStorage.setItem(
        'datarescueChatRecentCharts',
        JSON.stringify(this.recentCharts)
      );
    } catch (error) {
      console.error('Unable to save recent charts:', error);
      this.errorMessage =
        'Unable to save recent charts in this browser.';
    }
  }
}
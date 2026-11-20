import { Routes } from '@angular/router';

import { Dashboard } from './dashboard/dashboard';
import { Upload } from './upload/upload';
import { DatasetDetail } from './dataset-detail/dataset-detail';
import { DatasetPreviewComponent } from './dataset-preview/dataset-preview';
import { CleaningReport } from './cleaning-report/cleaning-report';

import { ChartBuilder } from './chart-builder/chart-builder';

export const routes: Routes = [

  {
    path: '',
    component: Dashboard
  },

  {
    path: 'upload',
    component: Upload
  },

  {
    path: 'dataset/:id',
    component: DatasetDetail
  },

  {
    path: 'dataset/:id/preview',
    component: DatasetPreviewComponent
  },

  {
    path: 'dataset/:id/report',
    component: CleaningReport
  },

  {
  path: 'chart-builder',
  component: ChartBuilder
  },

  {
    path: '**',
    redirectTo: ''
  }

];
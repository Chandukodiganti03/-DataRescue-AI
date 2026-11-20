
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectorRef, Component, ViewChild, ElementRef, OnInit } from '@angular/core';
import { Chatbot as ChatbotService, DatasetInfo } from '../services/chatbot';
import { RouterLink, Router } from '@angular/router';



interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}


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
  selector: 'app-chatbot',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink
  ],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css'
})


export class Chatbot implements OnInit {
  @ViewChild('messageContainer')
  messageContainer?: ElementRef<HTMLDivElement>;

  datasetId: number | null = 12;
  question = '';
  isOpen = false;
  isLoading = false;
  errorMessage = '';

  datasetName = '';
  datasets: DatasetInfo[] = [];

  messages: ChatMessage[] = [
    {
      role: 'assistant',
      text: 'Hello! I am DataRescue AI. Ask me questions about your dataset, data quality, missing values, and cleaning results.'
    }
  ];


  constructor(
  private chatbotService: ChatbotService,
  private cdr: ChangeDetectorRef,
  private router: Router
) {}

  toggleChat(): void {
    this.isOpen = !this.isOpen;
    this.cdr.detectChanges();

    if (this.isOpen) {
      setTimeout(() => this.scrollToBottom(), 100);
    }
  }

  ngOnInit(): void {



    this.loadRecentCharts();



  this.chatbotService.getDatasets().subscribe({
    next: (datasets) => {
      this.datasets = Array.isArray(datasets) ? datasets : [];
      this.updateDatasetName();
      this.cdr.detectChanges();
    },
    error: (error) => {
      console.error('Unable to load dataset names:', error);
    }
  });
}

updateDatasetName(): void {
  const selected = this.datasets.find(
    (dataset) => Number(dataset.id) === Number(this.datasetId)
  );

  this.datasetName = selected
    ? selected.fileName || selected.filename || selected.name || ''
    : '';
}

  sendMessage(): void {
    const message = this.question.trim();

    if (!message || this.isLoading) return;

    if (this.datasetId === null || this.datasetId <= 0) {
      this.errorMessage = 'Enter a valid dataset ID.';
      return;
    }

    this.messages = [
      ...this.messages,
      { role: 'user', text: message }
    ];

    this.question = '';
    this.errorMessage = '';
    this.isLoading = true;
    this.cdr.detectChanges();

    setTimeout(() => this.scrollToBottom(), 0);

    this.chatbotService
      .askDatasetQuestion(this.datasetId, message)
      .subscribe({
        next: (response) => {
          const answer =
            typeof response?.answer === 'string' && response.answer.trim()
              ? response.answer
              : 'The server returned an empty answer.';

          this.messages = [
            ...this.messages,
            { role: 'assistant', text: answer }
          ];

          this.isLoading = false;
          this.errorMessage = '';

          this.cdr.detectChanges();
          setTimeout(() => this.scrollToBottom(), 100);

          console.log('Chat UI updated:', {
            messageCount: this.messages.length,
            loading: this.isLoading
          });
        },
        error: (error) => {
          this.errorMessage =
            error?.error?.error ||
            'Unable to contact DataRescue AI. Check the backend and dataset ID.';

          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

createNewChart(): void {
  this.showCharts = true;
  this.chartMode = 'create';
  if (!this.datasets.length) {
    this.chartError = 'No datasets available to create a chart.';
    return;
  }

  const metric = this.selectedChartMetric;
  const option = this.chartMetricOptions.find(
    item => item.value === metric
  );

  const values = this.datasets.map(dataset => {
    const rawValue = dataset[metric];
    const value = Number(rawValue);

    const fileName =
      dataset.fileName || dataset.filename || dataset.name || 'Excel file';

    return {
      label: `${fileName} (ID: ${dataset.id})`,
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
  this.chartError = '';
  this.saveRecentCharts();
}

showChart(chart: RecentChart): void {
  this.chartMode = 'recent';
  this.activeChart = chart;
  this.chartError = '';
}

private loadRecentCharts(): void {
  try {
    const saved = localStorage.getItem('datarescueChatRecentCharts');

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
    this.chartError = 'Unable to save recent charts in this browser.';
  }
}







  private scrollToBottom(): void {
    const container = this.messageContainer?.nativeElement;

    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }



selectedChartMetric: ChartMetric = 'qualityScore';

chartMetricOptions: { value: ChartMetric; label: string }[] = [
  { value: 'qualityScore', label: 'Data Quality Score' },
  { value: 'missingValues', label: 'Missing Values' },
  { value: 'duplicateRows', label: 'Duplicate Rows' },
  { value: 'totalRows', label: 'Total Rows' },
  { value: 'totalColumns', label: 'Total Columns' }
];

recentCharts: RecentChart[] = [];
activeChart: RecentChart | null = null;
chartError = '';


showCharts = false;

toggleCharts(): void {
  this.showCharts = !this.showCharts;
}


chartMode: 'create' | 'recent' = 'create';

isChartChat = false;
private previousMessages: ChatMessage[] = [];

startNewChartChat(): void {
  if (!this.isChartChat) {
    this.previousMessages = [...this.messages];
  }

  this.isChartChat = true;
  this.showCharts = false;
  this.question = '';
  this.errorMessage = '';
  this.activeChart = null;

  this.messages = [{
    role: 'assistant',
    text: 'Welcome to your new chart conversation! Tell me what you want to visualize, compare, or understand about your dataset.'
  }];

  this.cdr.detectChanges();
}

returnToDatasetChat(): void {
  this.isChartChat = false;

  if (this.previousMessages.length > 0) {
    this.messages = [...this.previousMessages];
  }

  this.question = '';
  this.errorMessage = '';
  this.cdr.detectChanges();
}







openChartCreator(): void {
  this.showCharts = true;
  this.chartMode = 'create';
  this.activeChart = null;
  this.chartError = '';
}

openRecentCharts(): void {
  this.showCharts = true;
  this.chartMode = 'recent';
  this.activeChart = this.recentCharts[0] ?? null;
  this.chartError = '';
}

openChartBuilder(): void {
  this.showCharts = false;
  this.isOpen = false;
  this.cdr.detectChanges();

  this.router.navigate(['/chart-builder']);
}






}

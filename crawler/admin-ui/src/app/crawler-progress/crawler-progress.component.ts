import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { CrawlingService } from '../service/crawling.service';
import { CrawlerStatus, CrawlingProgress, CrawlingStatus } from '../type/crawling-progress.type';

const CRAWLER_STATUS_LABELS: Record<CrawlerStatus, string> = {
  RUNNING: 'Running',
  FINISHED: 'Finished',
  ERROR: 'Error'
};

const CRAWLING_STATUS_LABELS: Record<CrawlingStatus, string> = {
  IDLE: 'Idle',
  RUNNING: 'Running',
  STOPPING: 'Stopping',
  FINISHED: 'Finished',
  STOPPED: 'Stopped',
  FAILED: 'Failed'
};

@Component({
  selector: 'app-crawler-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './crawler-progress.component.html'
})
export class CrawlerProgressComponent implements OnInit, OnDestroy {
  protected readonly crawlerStatusLabels = CRAWLER_STATUS_LABELS;
  protected readonly crawlingStatusLabels = CRAWLING_STATUS_LABELS;
  protected progress: CrawlingProgress = {
    crawlers: [],
    status: 'IDLE',
    finished: false,
    new: 0,
    total: 0,
    outdated:0,
    started_at: null,
    finished_at: null
  };
  protected actionError: string | null = null;
  private progressInterval: any;
  constructor(private crawlingService: CrawlingService){};

  ngOnInit(): void {
    // Crawling may already be running, e.g. after a page reload, so keep following it
    this.crawlingService.getProgress().subscribe((pr) => {
      this.progress = pr;
      if (this.isCrawlingRunning) {
        this.pollProgress();
      }
    });
  }

  ngOnDestroy() {
    clearInterval(this.progressInterval);
  }

  protected get isCrawlingRunning(): boolean {
    return this.progress.status === 'RUNNING' || this.progress.status === 'STOPPING';
  }

  // A run can fail before any company is registered, and the empty state would hide that
  protected get isProgressShown(): boolean {
    return this.progress.crawlers.length > 0 || this.isCrawlingRunning || this.progress.status === 'FAILED';
  }

  startCrawling() {
    this.actionError = null;
    // Polling starts only once the crawler has reset its progress, otherwise the first poll can
    // still see the previous run as finished and stop right away
    this.crawlingService.startCrawling().subscribe({
      next: () => {
        this.progress = { ...this.progress, status: 'RUNNING' };
        this.pollProgress();
      },
      error: (error: HttpErrorResponse) => this.actionError = error.error?.message ?? 'Could not start crawling'
    });
  }

  stopCrawling() {
    this.actionError = null;
    this.crawlingService.stopCrawling().subscribe({
      next: () => this.progress = { ...this.progress, status: 'STOPPING' },
      error: (error: HttpErrorResponse) => this.actionError = error.error?.message ?? 'Could not stop crawling'
    });
  }

  private pollProgress() {
    clearInterval(this.progressInterval);
    this.progressInterval = setInterval(() => {
        this.crawlingService.getProgress().subscribe(
        (pr) => {
          this.progress = pr;
          if (!this.isCrawlingRunning) {
            clearInterval(this.progressInterval);
          }
        }
        );
    }, 1000);
  }
}

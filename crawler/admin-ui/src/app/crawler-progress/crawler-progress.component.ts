import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CrawlingService } from '../service/crawling.service';
import { CrawlerStatus, CrawlingProgress, CrawlingRunSummary, CrawlingStatus } from '../type/crawling-progress.type';

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
  imports: [CommonModule, RouterLink],
  templateUrl: './crawler-progress.component.html'
})
export class CrawlerProgressComponent implements OnInit, OnDestroy {
  protected readonly crawlerStatusLabels = CRAWLER_STATUS_LABELS;
  protected readonly crawlingStatusLabels = CRAWLING_STATUS_LABELS;
  protected progress: CrawlingProgress = {
    crawling_hash: '',
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
  /** The stored run this page shows, or null when it follows the live one. */
  protected crawlingHash: string | null = null;
  protected crawlingRuns: CrawlingRunSummary[] = [];
  private progressInterval: any;
  private routeSubscription?: Subscription;
  constructor(private crawlingService: CrawlingService, private route: ActivatedRoute){};

  ngOnInit(): void {
    // The router keeps this component when only the run in the URL changes
    this.routeSubscription = this.route.paramMap.subscribe((params) => {
      clearInterval(this.progressInterval);
      this.actionError = null;
      this.crawlingHash = params.get('crawlingHash');
      if (this.crawlingHash) {
        this.loadCrawlingRun(this.crawlingHash);
      } else {
        this.followLiveProgress();
      }
    });
  }

  ngOnDestroy() {
    clearInterval(this.progressInterval);
    this.routeSubscription?.unsubscribe();
  }

  protected get isCrawlingRunning(): boolean {
    return this.progress.status === 'RUNNING' || this.progress.status === 'STOPPING';
  }

  // A run can fail before any company is registered, and the empty state would hide that
  protected get isProgressShown(): boolean {
    if (this.crawlingHash) {
      return this.progress.crawling_hash === this.crawlingHash;
    }
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

  private followLiveProgress() {
    this.loadCrawlingRuns();
    // Crawling may already be running, e.g. after a page reload, so keep following it
    this.crawlingService.getProgress().subscribe((pr) => {
      this.progress = pr;
      if (this.isCrawlingRunning) {
        this.pollProgress();
      }
    });
  }

  private loadCrawlingRun(crawlingHash: string) {
    this.crawlingService.getCrawlingRun(crawlingHash).subscribe({
      next: (run) => this.progress = run,
      error: (error: HttpErrorResponse) => this.actionError = error.status === 404
        ? 'This crawling run is not in the history'
        : 'Could not load the crawling run'
    });
  }

  private loadCrawlingRuns() {
    this.crawlingService.getCrawlingRuns().subscribe((runs) => this.crawlingRuns = runs);
  }

  private pollProgress() {
    clearInterval(this.progressInterval);
    this.progressInterval = setInterval(() => {
        this.crawlingService.getProgress().subscribe(
        (pr) => {
          this.progress = pr;
          if (!this.isCrawlingRunning) {
            clearInterval(this.progressInterval);
            // The run is stored once it ends
            this.loadCrawlingRuns();
          }
        }
        );
    }, 1000);
  }
}

import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatStepperModule } from '@angular/material/stepper';
import { MatIconModule } from '@angular/material/icon';
import { ParsingService } from '../service/parsing.service';
import { ParserStatus, ParsingProgress, ParsingStatus } from '../type/parsing-progress.type';

const PARSER_STATUS_LABELS: Record<ParserStatus, string> = {
  RUNNING: 'Running',
  FINISHED: 'Finished',
  ERROR: 'Error'
};

const PARSING_STATUS_LABELS: Record<ParsingStatus, string> = {
  IDLE: 'Idle',
  RUNNING: 'Running',
  STOPPING: 'Stopping',
  FINISHED: 'Finished',
  STOPPED: 'Stopped',
  FAILED: 'Failed'
};

@Component({
  selector: 'app-parser-progress',
  standalone: true,
  imports: [CommonModule, MatStepperModule, MatIconModule],
  templateUrl: './parser-progress.component.html',
  styleUrl: './parser-progress.component.css',
  host: {
    '[class.custom-stepper]': 'true'
  }
})
export class ParserProgressComponent implements OnInit, OnDestroy {
  protected readonly parserStatusLabels = PARSER_STATUS_LABELS;
  protected readonly parsingStatusLabels = PARSING_STATUS_LABELS;
  protected progress: ParsingProgress = {
    parsers: [],
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
  constructor(private parsingService: ParsingService){};

  ngOnInit(): void {
    // Parsing may already be running, e.g. after a page reload, so keep following it
    this.parsingService.getProgress().subscribe((pr) => {
      this.progress = pr;
      if (this.isParsingRunning) {
        this.pollProgress();
      }
    });
  }

  ngOnDestroy() {
    clearInterval(this.progressInterval);
  }

  protected get isParsingRunning(): boolean {
    return this.progress.status === 'RUNNING' || this.progress.status === 'STOPPING';
  }

  // A run can fail before any company is registered, and the empty state would hide that
  protected get isProgressShown(): boolean {
    return this.progress.parsers.length > 0 || this.isParsingRunning || this.progress.status === 'FAILED';
  }

  startParsing() {
    this.actionError = null;
    // Polling starts only once the parser has reset its progress, otherwise the first poll can
    // still see the previous run as finished and stop right away
    this.parsingService.startParsing().subscribe({
      next: () => {
        this.progress = { ...this.progress, status: 'RUNNING' };
        this.pollProgress();
      },
      error: (error: HttpErrorResponse) => this.actionError = error.error?.message ?? 'Could not start parsing'
    });
  }

  stopParsing() {
    this.actionError = null;
    this.parsingService.stopParsing().subscribe({
      next: () => this.progress = { ...this.progress, status: 'STOPPING' },
      error: (error: HttpErrorResponse) => this.actionError = error.error?.message ?? 'Could not stop parsing'
    });
  }

  private pollProgress() {
    clearInterval(this.progressInterval);
    this.progressInterval = setInterval(() => {
        this.parsingService.getProgress().subscribe(
        (pr) => {
          this.progress = pr;
          if (!this.isParsingRunning) {
            clearInterval(this.progressInterval);
          }
        }
        );
    }, 1000);
  }
}

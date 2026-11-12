import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { FilterService } from '../service/filter.service';
import { AccountService } from '../service/account.service';
import { Filter, FilterRequest } from '../type/filter.type';
import { Connection, Provider } from '../type/account.type';

/** The select's "No chat (draft)" entry; never sent to the router. */
const DRAFT_OPTION = 'draft';

type ConnectionOption = number | typeof DRAFT_OPTION;

/** The form's state while a filter is being created (filterId null) or edited. */
type FilterDraft = {
  filterId: number | null
  title: string
  filter: string
  connectionOptions: ConnectionOption[]
}

/**
 * The signed-in user's filters: the list, and one form that creates or edits a filter.
 *
 * A filter notifies the linked chats it is bound to. Choosing "No chat (draft)" keeps it
 * without notifying anybody; it then matches nothing until it is bound.
 */
@Component({
  selector: 'app-filters',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatSelectModule],
  templateUrl: './filters.component.html'
})
export class FiltersComponent implements OnInit {
  protected readonly DRAFT_OPTION = DRAFT_OPTION;
  protected filters: Filter[] = [];
  protected connections: Connection[] = [];
  protected providers: Provider[] = [];
  protected draft: FilterDraft | null = null;
  protected isSaving: boolean = false;
  protected error: string | null = null;
  protected formError: string | null = null;

  constructor(private filterService: FilterService, private accountService: AccountService) { }

  ngOnInit(): void {
    forkJoin({
      filters: this.filterService.getFilters(),
      connections: this.accountService.getConnections(),
      providers: this.accountService.getProviders()
    }).subscribe({
      next: ({ filters, connections, providers }) => {
        this.filters = filters;
        this.connections = connections;
        this.providers = providers;
      },
      error: () => this.error = 'Could not load your filters.'
    });
  }

  private describeSaveError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400: return 'The expression could not be read. Check it against the syntax below.';
        case 404: return 'A chosen chat is no longer linked. Reload the page and choose again.';
      }
    }
    return 'Could not save the filter. Please try again.';
  }

  private toRequest(draft: FilterDraft): FilterRequest {
    return {
      title: draft.title.trim(),
      filter: draft.filter.trim(),
      connectionIds: draft.connectionOptions.filter((option): option is number => option !== DRAFT_OPTION)
    };
  }

  /** "Telegram · 473123822", followed by the chat's own title when it has one. */
  describeConnection(connection: Connection): string {
    const provider = this.providers.find((p) => p.id === connection.providerId)?.title ?? `Provider ${connection.providerId}`;
    const destination = `${provider} · ${connection.address ?? 'details missing'}`;
    return connection.title ? `${destination} (${connection.title})` : destination;
  }

  describeConnectionId(connectionId: number): string {
    const connection = this.connections.find((c) => c.id === connectionId);
    return connection ? this.describeConnection(connection) : `Chat ${connectionId}`;
  }

  startCreate(): void {
    this.formError = null;
    this.draft = { filterId: null, title: '', filter: '', connectionOptions: [DRAFT_OPTION] };
  }

  startEdit(filter: Filter): void {
    this.formError = null;
    this.draft = {
      filterId: filter.filterId,
      title: filter.title,
      filter: filter.filter,
      connectionOptions: filter.connectionIds.length > 0 ? [...filter.connectionIds] : [DRAFT_OPTION]
    };
  }

  cancelEdit(): void {
    this.draft = null;
  }

  /**
   * Keeps "No chat (draft)" exclusive: picking it clears the chats, picking a chat clears it,
   * and clearing every chat falls back to it.
   */
  onConnectionOptionsChange(options: ConnectionOption[]): void {
    const draft = this.draft;
    if (!draft) {
      return;
    }

    const wasDraft = draft.connectionOptions.includes(DRAFT_OPTION);
    let next = options;
    if (options.includes(DRAFT_OPTION) && !wasDraft) {
      next = [DRAFT_OPTION];
    } else if (options.length > 1) {
      next = options.filter((option) => option !== DRAFT_OPTION);
    }
    draft.connectionOptions = next.length > 0 ? next : [DRAFT_OPTION];
  }

  saveDraft(): void {
    const draft = this.draft;
    if (!draft || !draft.title.trim() || !draft.filter.trim()) {
      this.formError = 'A filter needs a title and an expression.';
      return;
    }

    this.isSaving = true;
    this.formError = null;
    const request = this.toRequest(draft);
    const save = draft.filterId === null
      ? this.filterService.createFilter(request)
      : this.filterService.updateFilter(draft.filterId, request);

    save.subscribe({
      next: (saved) => {
        const index = this.filters.findIndex((f) => f.filterId === saved.filterId);
        this.filters = index < 0
          ? [...this.filters, saved]
          : this.filters.map((f) => f.filterId === saved.filterId ? saved : f);
        this.draft = null;
        this.isSaving = false;
      },
      error: (error) => {
        this.formError = this.describeSaveError(error);
        this.isSaving = false;
      }
    });
  }

  deleteFilter(filter: Filter): void {
    if (!confirm(`Delete the filter "${filter.title}"?`)) {
      return;
    }

    this.filterService.deleteFilter(filter.filterId).subscribe({
      next: () => {
        this.filters = this.filters.filter((f) => f.filterId !== filter.filterId);
        if (this.draft?.filterId === filter.filterId) {
          this.draft = null;
        }
      },
      error: () => this.error = 'Could not delete the filter.'
    });
  }
}

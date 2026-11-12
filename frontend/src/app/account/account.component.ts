import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../service/auth.service';
import { AccountService } from '../service/account.service';
import { Connection, LinkSession, Provider } from '../type/account.type';

/** What a provider's chat expects before the code, by provider id: Telegram (1) takes a /link command. */
const LINK_CODE_PREFIXES: Record<number, string> = { 1: '/link ' };

/**
 * The signed-in user's own room: account details, linking a messenger, and the messengers
 * already linked.
 *
 * Linking is the same for every provider: start a session, show the provider's instructions,
 * then the user reloads the page, which lists the new connection once the provider reported it.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './account.component.html'
})
export class AccountComponent implements OnInit {
  protected providers: Provider[] = [];
  protected connections: Connection[] = [];
  protected linkSession: LinkSession | null = null;
  /** The session's one-time token and the instructions around it, from the start response only. */
  protected linkToken: string | null = null;
  protected linkInstructions: string | null = null;
  protected isLinkCodeCopied: boolean = false;
  protected editedConnectionId: number | null = null;
  protected editedTitle: string = '';
  protected error: string | null = null;

  constructor(protected authService: AuthService, private accountService: AccountService) { }

  ngOnInit(): void {
    this.loadProviders();
    this.loadConnections();
  }

  private loadProviders(): void {
    this.accountService.getProviders().subscribe({
      next: (providers) => this.providers = providers,
      error: () => this.error = 'Could not load the available messengers.'
    });
  }

  private loadConnections(): void {
    this.accountService.getConnections().subscribe({
      next: (connections) => this.connections = connections,
      error: () => this.error = 'Could not load the linked messengers.'
    });
  }

  providerTitle(providerId: number): string {
    return this.providers.find((provider) => provider.id === providerId)?.title ?? `Provider ${providerId}`;
  }

  startLink(provider: Provider): void {
    this.error = null;
    this.accountService.startLink(provider).subscribe({
      next: (session) => {
        this.linkSession = session;
        this.linkToken = session.token;
        this.linkInstructions = session.instructions;
        this.isLinkCodeCopied = false;
        if (session.status !== 'PENDING') {
          this.loadConnections();
        }
      },
      error: () => this.error = `Could not start linking ${provider.title}.`
    });
  }

  cancelLink(): void {
    this.linkSession = null;
    this.linkToken = null;
    this.linkInstructions = null;
  }

  /** The code as the user should send it: with the provider's command prefix, if it has one. */
  protected get linkSnippet(): string | null {
    if (!this.linkToken || !this.linkSession) {
      return null;
    }
    return (LINK_CODE_PREFIXES[this.linkSession.providerId] ?? '') + this.linkToken;
  }

  copyLinkToken(): void {
    const snippet = this.linkSnippet;
    if (snippet) {
      navigator.clipboard.writeText(snippet).then(() => this.isLinkCodeCopied = true);
    }
  }

  startRename(connection: Connection): void {
    this.editedConnectionId = connection.id;
    this.editedTitle = connection.title ?? '';
  }

  cancelRename(): void {
    this.editedConnectionId = null;
  }

  saveRename(connection: Connection): void {
    const title = this.editedTitle.trim();
    if (!title) {
      return;
    }

    this.accountService.renameConnection(connection, title).subscribe({
      next: (renamed) => {
        this.connections = this.connections.map((c) => c.id === renamed.id ? renamed : c);
        this.editedConnectionId = null;
      },
      error: () => this.error = 'Could not rename the connection.'
    });
  }

  deleteConnection(connection: Connection): void {
    if (!confirm('Unlink this chat? It will stop receiving notifications, and filters that notify only this chat become drafts.')) {
      return;
    }

    this.accountService.deleteConnection(connection).subscribe({
      next: () => this.connections = this.connections.filter((c) => c !== connection),
      error: () => this.error = 'Could not unlink the connection.'
    });
  }
}

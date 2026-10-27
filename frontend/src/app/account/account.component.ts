import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../service/auth.service';
import { AccountService } from '../service/account.service';
import { Connection } from '../type/account.type';

/**
 * The signed-in user's own room: account details, the API key used to link a messenger,
 * and the messengers already linked.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account.component.html'
})
export class AccountComponent implements OnInit {
  protected connections: Connection[] = [];
  protected generatedApiKey: string | null = null;
  protected isApiKeyCopied: boolean = false;
  protected editedConnectionId: number | null = null;
  protected editedTitle: string = '';
  protected error: string | null = null;

  constructor(protected authService: AuthService, private accountService: AccountService) { }

  ngOnInit(): void {
    this.loadConnections();
  }

  private loadConnections(): void {
    this.accountService.getConnections().subscribe({
      next: (connections) => this.connections = connections,
      error: () => this.error = 'Could not load the linked messengers.'
    });
  }

  generateApiKey(): void {
    const hasApiKey = this.authService.account()?.hasApiKey;
    if (hasApiKey && !confirm('The current key will stop working for new links. Generate a new one?')) {
      return;
    }

    this.accountService.generateApiKey().subscribe({
      next: ({ apiKey }) => {
        this.generatedApiKey = apiKey;
        this.isApiKeyCopied = false;
        this.authService.loadAccount().subscribe();
      },
      error: () => this.error = 'Could not generate an API key.'
    });
  }

  copyApiKey(): void {
    if (this.generatedApiKey) {
      navigator.clipboard.writeText(this.generatedApiKey).then(() => this.isApiKeyCopied = true);
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
        this.connections = this.connections.map((c) => c.id === renamed.id && c.provider === renamed.provider ? renamed : c);
        this.editedConnectionId = null;
      },
      error: () => this.error = 'Could not rename the connection.'
    });
  }

  deleteConnection(connection: Connection): void {
    if (!confirm('Unlink this chat? It will stop receiving notifications.')) {
      return;
    }

    this.accountService.deleteConnection(connection).subscribe({
      next: () => this.connections = this.connections.filter((c) => c !== connection),
      error: () => this.error = 'Could not unlink the connection.'
    });
  }
}

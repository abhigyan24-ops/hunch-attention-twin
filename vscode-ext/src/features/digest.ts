/**
 * digest.ts — Held Notifications Digest Manager.
 *
 * Stores held notification summaries during deep focus and presents
 * them quietly at natural break transitions.
 */

import type { EngineClient } from '../link/client.ts';
import type { Outbox } from '../link/outbox.ts';
import { PrivacyGate } from '../privacy/gate.ts';

export interface DigestItem {
  id: string;
  title?: string;
  category?: string;
  ts?: number;
}

export class DigestManager {
  private items: DigestItem[] = [];
  private engineClient: EngineClient | null;
  private outbox: Outbox;
  private vscodeApi: any;
  private onDigestChange?: () => void;

  constructor(options: {
    engineClient: EngineClient | null;
    outbox: Outbox;
    vscodeApi: any;
    onDigestChange?: () => void;
  }) {
    this.engineClient = options.engineClient;
    this.outbox = options.outbox;
    this.vscodeApi = options.vscodeApi;
    this.onDigestChange = options.onDigestChange;
  }

  setEngineClient(client: EngineClient | null): void {
    this.engineClient = client;
  }

  updateItems(newItems: DigestItem[]): void {
    this.items = [...newItems];
    this.onDigestChange?.();
  }

  getItems(): ReadonlyArray<DigestItem> {
    return this.items;
  }

  getItemCount(): number {
    return this.items.length;
  }

  clearItems(): void {
    this.items = [];
    this.onDigestChange?.();
  }

  /**
   * Called at natural break transitions (save_green, test_ok, etc.).
   * If dhyan.digest.toastAtBreak is enabled, alerts the user to held items.
   */
  notifyAtBreak(): void {
    if (this.items.length === 0) return;

    const config = this.vscodeApi?.workspace?.getConfiguration?.('dhyan');
    const allowToast = Boolean(config?.get('digest.toastAtBreak', false));

    if (allowToast && this.vscodeApi?.window?.showInformationMessage) {
      this.vscodeApi.window
        .showInformationMessage(
          `Dhyan: ${this.items.length} notification${this.items.length > 1 ? 's were' : ' was'} held during your focus.`,
          'Review Digest',
          'Dismiss'
        )
        .then((choice: string) => {
          if (choice === 'Review Digest') {
            this.openDigest();
          }
        });
    }
  }

  /**
   * Opens the digest QuickPick for reviewing held notifications.
   */
  async openDigest(): Promise<void> {
    if (this.items.length === 0) {
      if (this.vscodeApi?.window?.showInformationMessage) {
        this.vscodeApi.window.showInformationMessage(
          'Dhyan: No held notifications. Your attention was completely uninterrupted.'
        );
      }
      return;
    }

    if (!this.vscodeApi?.window?.showQuickPick) return;

    const pickItems = this.items.map(item => ({
      label: item.title || item.category || 'Notification',
      description: item.category ? `[${item.category}]` : undefined,
      detail: item.ts ? new Date(item.ts).toLocaleTimeString() : undefined,
      itemId: item.id
    }));

    pickItems.push({
      label: 'Clear All Held Notifications',
      description: 'Mark all as reviewed',
      detail: undefined,
      itemId: '__clear_all__'
    });

    const selected = await this.vscodeApi.window.showQuickPick(pickItems, {
      title: `Dhyan: Held Notifications (${this.items.length})`
    });

    if (selected) {
      if (selected.itemId === '__clear_all__') {
        for (const item of this.items) {
          this.sendFeedback(item.id, 'dismiss');
        }
        this.clearItems();
      } else {
        this.sendFeedback(selected.itemId, 'click');
        // Remove reviewed item
        this.items = this.items.filter(i => i.id !== selected.itemId);
        this.onDigestChange?.();
      }
    }
  }

  private sendFeedback(id: string, action: 'useful' | 'not_useful' | 'dismiss' | 'click'): void {
    this.engineClient?.sendFeedback(id, 'digest', action);

    const feedbackEvent = {
      type: 'ed_feedback',
      id: id.slice(0, 64),
      target: 'digest',
      action
    };

    const sanitized = PrivacyGate.sanitize(feedbackEvent);
    if (sanitized) {
      this.outbox.enqueue(sanitized);
    }
  }
}

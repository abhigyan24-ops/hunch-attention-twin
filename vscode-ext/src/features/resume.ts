/**
 * resume.ts — Editor state snapshotting and resumption feature.
 *
 * Privacy rules (HARD):
 *   - Rule P5 / 16.4: Snapshots with file paths and line numbers live strictly
 *     in context.workspaceState and NEVER enter events or outbound messages.
 *   - Respects user privacy exclusions: never snapshots excluded files (.env, etc.).
 */

import * as path from 'node:path';
import type { PrivacyFilter } from '../privacy/exclude.ts';
import type { ResumeTracker } from '../aggregate/resumeTracker.ts';
import type { DhyanStatusBar } from '../ui/statusBar.ts';

export interface EditorSnapshot {
  filePath: string;
  fileName: string;
  line: number;
  character: number;
  taskKey: string;
  timestamp: number;
  parkedNote?: string;
}

export interface WorkspaceStateLike {
  get<T>(key: string): T | undefined;
  update(key: string, value: any): Thenable<void> | Promise<void>;
}

export const SNAPSHOT_STATE_KEY = 'dhyan.lastSnapshot';

export class ResumeManager {
  private vscodeApi: any;
  private workspaceState: WorkspaceStateLike;
  private privacyFilter: PrivacyFilter;
  private resumeTracker: ResumeTracker;
  private statusBar: DhyanStatusBar | null;
  private onSnapshotChanged?: () => void;

  private idleTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(options: {
    vscodeApi: any;
    workspaceState: WorkspaceStateLike;
    privacyFilter: PrivacyFilter;
    resumeTracker: ResumeTracker;
    statusBar: DhyanStatusBar | null;
    onSnapshotChanged?: () => void;
  }) {
    this.vscodeApi = options.vscodeApi;
    this.workspaceState = options.workspaceState;
    this.privacyFilter = options.privacyFilter;
    this.resumeTracker = options.resumeTracker;
    this.statusBar = options.statusBar;
    this.onSnapshotChanged = options.onSnapshotChanged;

    this.startIdleMonitoring();
  }

  /**
   * Resets the 60s idle detection timer on user activity.
   */
  recordActivity(): void {
    this.resetIdleTimer();
  }

  private startIdleMonitoring(): void {
    this.resetIdleTimer();
  }

  private resetIdleTimer(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
    }
    // Idle >= 60 seconds triggers a snapshot save
    this.idleTimer = setTimeout(() => {
      this.captureSnapshot();
    }, 60_000);
  }

  /**
   * Captures the current active editor position and persists it to workspaceState.
   * Checks privacyFilter to ensure excluded files are NEVER snapshotted.
   */
  captureSnapshot(taskKey = '0000000000000000', customNote?: string): EditorSnapshot | null {
    try {
      const activeEditor = this.vscodeApi?.window?.activeTextEditor;
      if (!activeEditor || !activeEditor.document) {
        return null;
      }

      const doc = activeEditor.document;
      const uri = doc.uri;

      // Privacy check: NEVER snapshot excluded files
      if (this.privacyFilter.isExcluded(doc)) {
        return null;
      }

      const filePath = uri.fsPath || uri.path || '';
      if (!filePath) {
        return null;
      }

      const fileName = path.basename(filePath);
      const position = activeEditor.selection?.active || { line: 0, character: 0 };

      const existing = this.getSnapshot();
      const snapshot: EditorSnapshot = {
        filePath,
        fileName,
        line: position.line,
        character: position.character,
        taskKey,
        timestamp: Date.now(),
        parkedNote: customNote !== undefined ? customNote : existing?.parkedNote
      };

      this.workspaceState.update(SNAPSHOT_STATE_KEY, snapshot);
      this.onSnapshotChanged?.();

      return snapshot;
    } catch {
      return null;
    }
  }

  /**
   * Retrieves the current snapshot from workspaceState.
   */
  getSnapshot(): EditorSnapshot | null {
    try {
      return this.workspaceState.get<EditorSnapshot>(SNAPSHOT_STATE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  /**
   * Clears the current snapshot.
   */
  clearSnapshot(): void {
    try {
      this.workspaceState.update(SNAPSHOT_STATE_KEY, undefined);
      this.onSnapshotChanged?.();
    } catch {}
  }

  /**
   * Executes the `dhyan.resume` command:
   * Opens the snapshotted file, restores cursor position, and registers assist usage.
   */
  async executeResume(): Promise<boolean> {
    const snapshot = this.getSnapshot();
    if (!snapshot || !snapshot.filePath) {
      if (this.vscodeApi?.window?.showInformationMessage) {
        this.vscodeApi.window.showInformationMessage(
          'Dhyan: No previous interrupted location found in this workspace.'
        );
      }
      return false;
    }

    try {
      // 1. Mark assist as used in ResumeTracker
      this.resumeTracker.onAssistUsed();

      // 2. Open document and reveal cursor position
      const doc = await this.vscodeApi.workspace.openTextDocument(snapshot.filePath);
      const editor = await this.vscodeApi.window.showTextDocument(doc, {
        preview: false
      });

      const pos = new this.vscodeApi.Position(snapshot.line, snapshot.character);
      const selection = new this.vscodeApi.Selection(pos, pos);
      editor.selection = selection;
      editor.revealRange(
        new this.vscodeApi.Range(pos, pos),
        this.vscodeApi.TextEditorRevealType?.InCenterIfOutsideViewport ?? 1
      );

      // 3. Clear status bar resume card
      if (this.statusBar && this.statusBar.getMode() === 'resume') {
        this.statusBar.setFocused(0, 0);
      }

      if (this.vscodeApi?.window?.setStatusBarMessage) {
        this.vscodeApi.window.setStatusBarMessage(
          `Dhyan: Resumed at ${snapshot.fileName}:${snapshot.line + 1}`,
          3000
        );
      }

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Executes the `dhyan.park` command:
   * Prompts user for a quick intention/note, captures editor state, and stores locally.
   */
  async executePark(taskKey = '0000000000000000'): Promise<void> {
    try {
      if (!this.vscodeApi?.window?.showInputBox) return;

      const note = await this.vscodeApi.window.showInputBox({
        title: 'Dhyan: Park Intention',
        prompt: 'Note where you are leaving off (stored locally in workspace only, never transmitted):',
        placeHolder: 'e.g., Implementing auth token retry backoff'
      });

      if (note === undefined) {
        // User cancelled
        return;
      }

      this.captureSnapshot(taskKey, note.trim());

      if (this.vscodeApi?.window?.setStatusBarMessage) {
        this.vscodeApi.window.setStatusBarMessage('Dhyan: Intention note parked locally.', 3000);
      }
    } catch {}
  }

  dispose(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
  }
}

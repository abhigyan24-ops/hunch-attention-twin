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
  enclosingSymbol?: string;
  gitStatSummary?: string;
  lastBuildSummary?: string;
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
  private buildShield?: { getLastBuildSummary: () => string };

  private idleTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(options: {
    vscodeApi: any;
    workspaceState: WorkspaceStateLike;
    privacyFilter: PrivacyFilter;
    resumeTracker: ResumeTracker;
    statusBar: DhyanStatusBar | null;
    onSnapshotChanged?: () => void;
    buildShield?: { getLastBuildSummary: () => string };
  }) {
    this.vscodeApi = options.vscodeApi;
    this.workspaceState = options.workspaceState;
    this.privacyFilter = options.privacyFilter;
    this.resumeTracker = options.resumeTracker;
    this.statusBar = options.statusBar;
    this.onSnapshotChanged = options.onSnapshotChanged;
    this.buildShield = options.buildShield;

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

      const fileName = path.basename(filePath.replace(/\\/g, '/'));
      const position = activeEditor.selection?.active || { line: 0, character: 0 };

      // Extract enclosing symbol (e.g. fn / method signature)
      let enclosingSymbol = '';
      try {
        const text = doc.getText();
        const lines = text.split(/\r?\n/);
        const symbolRegex = /(?:function\s+([a-zA-Z0-9_$]+)|class\s+([a-zA-Z0-9_$]+)|def\s+([a-zA-Z0-9_$]+)|const\s+([a-zA-Z0-9_$]+)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[a-zA-Z0-9_$]+)\s*=>|([a-zA-Z0-9_$]+)\s*\([^)]*\)\s*[:{])/;
        for (let i = position.line; i >= 0 && i >= position.line - 60; i--) {
          const match = symbolRegex.exec(lines[i] || '');
          if (match) {
            const sym = match[1] || match[2] || match[3] || match[4] || match[5];
            if (sym && !['if', 'for', 'while', 'switch', 'catch'].includes(sym)) {
              enclosingSymbol = `${sym}()`;
              break;
            }
          }
        }
      } catch {}

      const existing = this.getSnapshot();
      const lastBuildSummary = this.buildShield?.getLastBuildSummary() || existing?.lastBuildSummary || '';

      const snapshot: EditorSnapshot = {
        filePath,
        fileName,
        line: position.line,
        character: position.character,
        taskKey,
        timestamp: Date.now(),
        parkedNote: customNote !== undefined ? customNote : existing?.parkedNote,
        enclosingSymbol,
        gitStatSummary: existing?.gitStatSummary,
        lastBuildSummary,
      };

      this.workspaceState.update(SNAPSHOT_STATE_KEY, snapshot);
      this.onSnapshotChanged?.();

      // Sync snapshot with engine sidecar for cross-device and laptop dashboard
      try {
        const workspaceName = this.vscodeApi?.workspace?.workspaceFolders?.[0]?.name ?? '';
        fetch('http://127.0.0.1:8765/v1/insights/context-snapshot', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            snapshot_id: `snap_${Date.now()}`,
            created_at_ts: Date.now(),
            workspace_name: workspaceName,
            file_path: filePath,
            line_number: position.line + 1,
            column_number: position.character + 1,
            user_note: snapshot.parkedNote ?? '',
            enclosing_symbol: enclosingSymbol,
            git_stat_summary: snapshot.gitStatSummary ?? '',
            last_build_summary: lastBuildSummary,
          }),
        }).catch(() => {});
      } catch {}

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
          'Hunch: No previous interrupted location found in this workspace.'
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
          `Hunch: Resumed at ${snapshot.fileName}:${snapshot.line + 1}`,
          3000
        );
      }

      // Notify engine sidecar of resumption
      fetch('http://127.0.0.1:8765/v1/insights/context-snapshot/resume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          snapshot_id: 'latest',
          resumed_at_ts: Date.now(),
        }),
      }).catch(() => {});

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
        title: 'Hunch: Park Intention',
        prompt: 'Note where you are leaving off (stored locally in workspace only, never transmitted):',
        placeHolder: 'e.g., Implementing auth token retry backoff'
      });

      if (note === undefined) {
        // User cancelled
        return;
      }

      this.captureSnapshot(taskKey, note.trim());

      if (this.vscodeApi?.window?.setStatusBarMessage) {
        this.vscodeApi.window.setStatusBarMessage('Hunch: Intention note parked locally.', 3000);
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

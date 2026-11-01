import type { WindowAggregator } from '../aggregate/window.ts';
import { classifyEdit, isTrackedScheme, type ChangeItem } from '../aggregate/classify.ts';
import type { PrivacyFilter } from '../privacy/exclude.ts';

export interface EditorAdapterOptions {
  aggregator: WindowAggregator;
  privacyFilter: PrivacyFilter;
  vscode: any;
}

export class EditorSensor {
  private aggregator: WindowAggregator;
  private privacyFilter: PrivacyFilter;
  private disposables: { dispose(): void }[] = [];

  constructor(options: EditorAdapterOptions) {
    this.aggregator = options.aggregator;
    this.privacyFilter = options.privacyFilter;
    this.setup(options.vscode);
  }

  private setup(vsc: any): void {
    if (!vsc) return;

    // 1. Document changes (edits)
    if (vsc.workspace?.onDidChangeTextDocument) {
      this.disposables.push(
        vsc.workspace.onDidChangeTextDocument((e: any) => {
          this.handleDocChange(e, vsc);
        })
      );
    }

    // 2. Cursor selection changes
    if (vsc.window?.onDidChangeTextEditorSelection) {
      this.disposables.push(
        vsc.window.onDidChangeTextEditorSelection((e: any) => {
          if (!e?.textEditor?.document) return;
          if (!isTrackedScheme(e.textEditor.document.uri?.scheme)) return;
          if (this.privacyFilter.isExcluded(e.textEditor.document)) return;
          this.aggregator.onCursorMove();
        })
      );
    }

    // 3. Scroll / Visible ranges
    if (vsc.window?.onDidChangeTextEditorVisibleRanges) {
      this.disposables.push(
        vsc.window.onDidChangeTextEditorVisibleRanges((e: any) => {
          if (!e?.textEditor?.document) return;
          if (!isTrackedScheme(e.textEditor.document.uri?.scheme)) return;
          if (this.privacyFilter.isExcluded(e.textEditor.document)) return;
          this.aggregator.onScroll();
        })
      );
    }

    // 4. Document saves
    if (vsc.workspace?.onDidSaveTextDocument) {
      this.disposables.push(
        vsc.workspace.onDidSaveTextDocument((doc: any) => {
          if (!isTrackedScheme(doc?.uri?.scheme)) return;
          if (this.privacyFilter.isExcluded(doc)) return;
          this.aggregator.onSave();
        })
      );
    }

    // 5. Active editor change (file switch)
    if (vsc.window?.onDidChangeActiveTextEditor) {
      this.disposables.push(
        vsc.window.onDidChangeActiveTextEditor((editor: any) => {
          if (!editor?.document) return;
          if (!isTrackedScheme(editor.document.uri?.scheme)) return;
          if (this.privacyFilter.isExcluded(editor.document)) return;
          this.aggregator.onFileSwitch(editor.document.languageId);
        })
      );
    }
  }

  public handleDocChange(e: any, vsc: any): void {
    const doc = e?.document;
    if (!doc) return;
    if (!isTrackedScheme(doc.uri?.scheme)) return;
    if (this.privacyFilter.isExcluded(doc)) return;

    // Hot-path rule F1: Extract numeric lengths only! NEVER read text content.
    const changes = e.contentChanges;
    if (!changes || !Array.isArray(changes) || changes.length === 0) {
      return;
    }

    let ins = 0;
    let del = 0;
    const changeItems: ChangeItem[] = new Array(changes.length);

    for (let i = 0; i < changes.length; i++) {
      const c = changes[i];
      const textLen = c.text?.length ?? 0;
      const rangeLen = c.rangeLength ?? 0;
      ins += textLen;
      del += rangeLen;
      changeItems[i] = { textLen, rangeLen };
    }

    const activeEditor = vsc?.window?.activeTextEditor;
    const isActiveDoc = Boolean(activeEditor?.document === doc);
    const windowActive = Boolean(vsc?.window?.state?.active ?? vsc?.window?.state?.focused ?? true);

    let reason: 'undo' | 'redo' | undefined = undefined;
    if (e.reason === 1 || e.reason === 'undo') {
      reason = 'undo';
    } else if (e.reason === 2 || e.reason === 'redo') {
      reason = 'redo';
    }

    const kind = classifyEdit({
      reason,
      changes: changeItems,
      isActiveDoc,
      windowActive
    });

    this.aggregator.onEdit(kind, ins, del, Date.now());
  }

  dispose(): void {
    for (const d of this.disposables) {
      d.dispose();
    }
    this.disposables = [];
  }
}

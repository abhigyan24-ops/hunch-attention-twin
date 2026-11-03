import type { WindowAggregator } from '../aggregate/window.ts';

export class DebugSensor {
  private disposables: { dispose(): void }[] = [];

  constructor(
    vscode: any,
    aggregator: WindowAggregator,
    onDebugStateChange?: (phase: 'start' | 'pause' | 'continue' | 'stop') => void
  ) {
    if (vscode?.debug) {
      if (vscode.debug.onDidStartDebugSession) {
        this.disposables.push(
          vscode.debug.onDidStartDebugSession(() => {
            aggregator.onDebug('start');
            onDebugStateChange?.('start');
          })
        );
      }

      if (vscode.debug.onDidTerminateDebugSession) {
        this.disposables.push(
          vscode.debug.onDidTerminateDebugSession(() => {
            aggregator.onDebug('stop');
            onDebugStateChange?.('stop');
          })
        );
      }

      if (vscode.debug.onDidChangeActiveDebugSession) {
        this.disposables.push(
          vscode.debug.onDidChangeActiveDebugSession((session: any) => {
            if (session) {
              aggregator.onDebug('continue');
              onDebugStateChange?.('continue');
            }
          })
        );
      }
    }
  }

  dispose(): void {
    for (const d of this.disposables) {
      d.dispose();
    }
    this.disposables = [];
  }
}

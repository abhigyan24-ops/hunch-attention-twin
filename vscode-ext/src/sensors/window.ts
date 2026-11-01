import type { WindowAggregator } from '../aggregate/window.ts';
import type { Clock } from '../clock.ts';

export class WindowSensor {
  private disposables: { dispose(): void }[] = [];

  constructor(vscode: any, aggregator: WindowAggregator, clock: Clock) {
    if (vscode?.window?.onDidChangeWindowState) {
      this.disposables.push(
        vscode.window.onDidChangeWindowState((state: any) => {
          const focused = Boolean(state?.focused);
          const active = Boolean(state?.active ?? focused);
          aggregator.onWindow(focused, active, clock.mono());
        })
      );
    }
  }

  dispose(): void {
    for (const d of this.disposables) {
      d.dispose();
    }
    this.disposables = [];
  }
}

import type { WindowAggregator } from '../aggregate/window.ts';
import type { Clock } from '../clock.ts';

export class DiagnosticsSensor {
  private disposables: { dispose(): void }[] = [];
  private lastReadMono = 0;
  private readonly minIntervalMs = 2000; // F5: At most once per 2 seconds

  constructor(vscode: any, aggregator: WindowAggregator, clock: Clock) {
    if (vscode?.languages?.onDidChangeDiagnostics) {
      this.disposables.push(
        vscode.languages.onDidChangeDiagnostics(() => {
          const nowMono = clock.mono();
          if (nowMono - this.lastReadMono < this.minIntervalMs) {
            return;
          }
          this.lastReadMono = nowMono;

          let errs = 0;
          let warns = 0;

          try {
            if (vscode.languages?.getDiagnostics) {
              const all = vscode.languages.getDiagnostics();
              for (const entry of all) {
                const list = entry[1];
                if (Array.isArray(list)) {
                  for (let i = 0; i < list.length; i++) {
                    const sev = list[i]?.severity;
                    if (sev === 0 || sev === 'Error') errs++;
                    else if (sev === 1 || sev === 'Warning') warns++;
                  }
                }
              }
            }
          } catch {
            // Ignore failure
          }

          aggregator.onDiagnostics(errs, warns);
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

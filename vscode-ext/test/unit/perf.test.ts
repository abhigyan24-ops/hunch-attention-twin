import assert from 'node:assert/strict';
import { FakeClock } from '../../src/clock.ts';
import { WindowAggregator } from '../../src/aggregate/window.ts';
import { PrivacyFilter } from '../../src/privacy/exclude.ts';
import { EditorSensor } from '../../src/sensors/editor.ts';

describe('Hot-Path Performance Benchmark (Rule F1)', () => {
  it('processes 10,000 rapid edit events with average handler latency < 0.05 ms', () => {
    const clock = new FakeClock();
    const aggregator = new WindowAggregator(clock);
    const privacyFilter = new PrivacyFilter();

    let docChangeHandler: ((e: any) => void) | null = null;
    const mockVscode = {
      workspace: {
        onDidChangeTextDocument: (cb: any) => {
          docChangeHandler = cb;
          return { dispose: () => {} };
        }
      },
      window: {
        activeTextEditor: {
          document: { uri: { scheme: 'file', fsPath: '/app/src/main.ts' }, languageId: 'typescript' }
        },
        state: { active: true, focused: true }
      }
    };

    const sensor = new EditorSensor({
      aggregator,
      privacyFilter,
      vscode: mockVscode
    });

    assert.ok(docChangeHandler, 'Document change handler must be registered');

    const testDoc = mockVscode.window.activeTextEditor.document;
    const mockEvent = {
      document: testDoc,
      contentChanges: [
        { text: 'x', rangeLength: 0 }
      ]
    };

    // Warm-up JIT
    for (let i = 0; i < 500; i++) {
      sensor.handleDocChange(mockEvent, mockVscode);
    }

    // Benchmark 10,000 iterations
    const iterations = 10000;
    const startTime = performance.now();

    for (let i = 0; i < iterations; i++) {
      sensor.handleDocChange(mockEvent, mockVscode);
    }

    const endTime = performance.now();
    const totalDurationMs = endTime - startTime;
    const avgLatencyMs = totalDurationMs / iterations;

    console.log(`    Performance Benchmark: 10,000 edits took ${totalDurationMs.toFixed(2)}ms (avg ${avgLatencyMs.toFixed(5)}ms per edit)`);

    assert.ok(
      avgLatencyMs < 0.05,
      `Average handler latency ${avgLatencyMs.toFixed(5)}ms exceeded 0.05ms budget!`
    );

    sensor.dispose();
  });
});

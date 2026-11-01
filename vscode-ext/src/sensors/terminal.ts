/**
 * terminal.ts — Thin vscode adapter for shell integration events.
 *
 * Uses VS Code's Shell Integration API (available in 1.93+) to detect
 * terminal command completions and classify them into categories.
 *
 * Privacy rules (HARD):
 *   - Raw command line text is NEVER read, stored, or emitted.
 *   - Classification is purely based on the first token of the command
 *     to a known-safe category set. If classification fails, 'other' is used.
 *   - Only category + exit code are recorded.
 *
 * Category set: 'git' | 'build' | 'test' | 'lint' | 'run' | 'nav' | 'edit' | 'pkg' | 'other'
 */

import type { Clock } from '../clock.ts';
import type { TaskContextTracker } from '../aggregate/taskContext.ts';
import type { Outbox } from '../link/outbox.ts';
import { PrivacyGate } from '../privacy/gate.ts';

export type TermCategory = 'git' | 'build' | 'test' | 'lint' | 'run' | 'nav' | 'edit' | 'pkg' | 'other';

// Mapping first token of command to category.
// IMPORTANT: We only ever read the first whitespace-delimited token (the binary name),
// never the arguments.
const FIRST_TOKEN_MAP: Record<string, TermCategory> = {
  // git
  git: 'git',
  gh: 'git',
  // build / compile
  make: 'build',
  cmake: 'build',
  gcc: 'build',
  g: 'build',
  clang: 'build',
  cargo: 'build', // overridden below for 'cargo test'
  go: 'build',    // overridden below for 'go test'
  tsc: 'build',
  webpack: 'build',
  esbuild: 'build',
  rollup: 'build',
  gradle: 'build',
  mvn: 'build',
  dotnet: 'build',
  // test runners
  jest: 'test',
  mocha: 'test',
  vitest: 'test',
  pytest: 'test',
  jasmine: 'test',
  karma: 'test',
  // lint / format
  eslint: 'lint',
  tslint: 'lint',
  flake8: 'lint',
  pylint: 'lint',
  prettier: 'lint',
  rubocop: 'lint',
  // run / serve
  node: 'run',
  python: 'run',
  python3: 'run',
  ruby: 'run',
  php: 'run',
  java: 'run',
  uvicorn: 'run',
  gunicorn: 'run',
  flask: 'run',
  // navigation / shell
  cd: 'nav',
  ls: 'nav',
  dir: 'nav',
  pwd: 'nav',
  find: 'nav',
  // file editing (terminal editors)
  vim: 'edit',
  nvim: 'edit',
  nano: 'edit',
  emacs: 'edit',
  code: 'edit',
  // package managers
  npm: 'pkg',
  npx: 'pkg',
  yarn: 'pkg',
  pnpm: 'pkg',
  pip: 'pkg',
  pip3: 'pkg',
  brew: 'pkg',
  apt: 'pkg',
  'apt-get': 'pkg',
  dnf: 'pkg',
  pacman: 'pkg'
};

/**
 * Classifies a command line string to a TermCategory by inspecting ONLY
 * the first whitespace-delimited token (the binary/command name).
 * The rest of the command line is discarded.
 */
export function classifyTerminalCommand(commandLine: string): TermCategory {
  try {
    // Extract first token only — everything after the first space is discarded
    const firstToken = commandLine.trimStart().split(/\s+/)[0].toLowerCase();
    if (!firstToken) return 'other';

    const category = FIRST_TOKEN_MAP[firstToken];
    if (category) return category;

    // Special sub-command classification (e.g., 'cargo test', 'go test', 'npm test')
    // Only reads the second token to check for known safe sub-command names
    const parts = commandLine.trimStart().split(/\s+/);
    if (parts.length >= 2) {
      const sub = parts[1].toLowerCase();
      if (firstToken === 'cargo' || firstToken === 'go') {
        if (sub === 'test') return 'test';
        if (sub === 'build') return 'build';
        if (sub === 'run') return 'run';
        if (sub === 'fmt') return 'lint';
      }
      if (firstToken === 'npm' || firstToken === 'npx' || firstToken === 'yarn' || firstToken === 'pnpm') {
        if (sub === 'run' && parts.length >= 3) {
          const script = parts[2].toLowerCase();
          if (/test/.test(script)) return 'test';
          if (/build|compile/.test(script)) return 'build';
          if (/lint|format/.test(script)) return 'lint';
          if (/start|dev|serve/.test(script)) return 'run';
        }
        if (sub === 'test') return 'test';
        if (sub === 'start') return 'run';
        if (sub === 'build') return 'build';
        if (sub === 'install' || sub === 'add' || sub === 'remove' || sub === 'uninstall') return 'pkg';
        // npm/yarn/pnpm are otherwise already 'pkg' from first token
        return 'pkg';
      }
      if (firstToken === 'dotnet') {
        if (sub === 'test') return 'test';
        if (sub === 'build') return 'build';
        if (sub === 'run') return 'run';
      }
    }

    return 'other';
  } catch {
    return 'other';
  }
}

export class TerminalSensor {
  private disposables: Array<{ dispose: () => void }> = [];
  private clock: Clock;
  private taskContextTracker: TaskContextTracker;
  private outbox: Outbox;

  constructor(
    vscodeApi: any,
    clock: Clock,
    taskContextTracker: TaskContextTracker,
    outbox: Outbox
  ) {
    this.clock = clock;
    this.taskContextTracker = taskContextTracker;
    this.outbox = outbox;

    try {
      // Feature-detect shell integration API (VS Code ≥1.93)
      if ('onDidEndTerminalShellExecution' in (vscodeApi?.window ?? {})) {
        const sub = vscodeApi.window.onDidEndTerminalShellExecution((event: any) => {
          this.handleShellExecution(event);
        });
        this.disposables.push(sub);
      }
    } catch {
      // Shell integration unavailable — silent no-op
    }
  }

  dispose(): void {
    for (const d of this.disposables) {
      try { d.dispose(); } catch {}
    }
    this.disposables = [];
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private handleShellExecution(event: any): void {
    try {
      // PRIVACY: We access shellIntegration.commandLine ONLY to extract
      // the first token for category classification. The full command line
      // is intentionally discarded after classifyTerminalCommand().
      const commandLine: string = event?.execution?.commandLine?.value ?? '';
      const exitCode: number = event?.exitCode ?? -1;

      // Classify — raw commandLine string is used only here and then discarded
      const category = classifyTerminalCommand(commandLine);

      // Notify TaskContextTracker (taskType inference)
      this.taskContextTracker.recordTerminalCmd(category);

      // Emit ed_term event (category + exitCode only, no raw text)
      const now = this.clock.now();
      const mono = this.clock.mono();
      const rawEvent = {
        type: 'ed_term',
        ts: now,
        mono,
        commandCategory: category,
        exitCode
      };

      const sanitized = PrivacyGate.sanitize(rawEvent);
      if (sanitized) {
        this.outbox.enqueue(sanitized);
      }
    } catch {
      // Swallow all errors — never interrupt the user
    }
  }
}

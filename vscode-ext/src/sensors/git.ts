/**
 * git.ts — Thin vscode adapter for the built-in Git extension.
 *
 * Privacy rules (HARD):
 *   - Workspace paths and branch names are NEVER emitted or logged.
 *   - Only the 16-hex taskKey hash derived by TaskContextTracker is ever sent.
 *   - Git HEAD SHA and branch are passed to BreakDetector for change detection only.
 *
 * Wraps `vscode.extensions.getExtension('vscode.git')?.exports.getAPI(1)` fully
 * in try/catch. If the git extension is absent or unavailable, all operations
 * are silent no-ops.
 */

import type { BreakDetector } from '../aggregate/breaks.ts';

export interface GitState {
  workspacePath: string | null;
  branchName: string | null;
  headCommit: string | null;
  uncommittedCount: number;
}

// Minimal type signatures for the vscode.git extension API (v1)
interface GitApiRepository {
  rootUri: { fsPath: string };
  state: {
    HEAD?: { name?: string; commit?: string };
    workingTreeChanges: unknown[];
    indexChanges: unknown[];
    onDidChange: (handler: () => void) => { dispose: () => void };
  };
}

interface GitApi {
  repositories: GitApiRepository[];
  onDidOpenRepository: (handler: (repo: GitApiRepository) => void) => { dispose: () => void };
  onDidCloseRepository: (handler: (repo: GitApiRepository) => void) => { dispose: () => void };
}

export class GitSensor {
  private breakDetector: BreakDetector;

  private gitApi: GitApi | null = null;
  private disposables: Array<{ dispose: () => void }> = [];

  // Cached git state for external read
  private _workspacePath: string | null = null;
  private _branchName: string | null = null;
  private _headCommit: string | null = null;
  private _uncommittedCount = 0;

  constructor(
    vscodeApi: any,
    breakDetector: BreakDetector,
    _taskContextTracker?: any,
    gitApiOverride?: GitApi
  ) {
    this.breakDetector = breakDetector;

    if (gitApiOverride) {
      this.gitApi = gitApiOverride;
      this.subscribeAll();
    } else {
      try {
        const ext = vscodeApi?.extensions?.getExtension?.('vscode.git');
        if (ext?.exports) {
          this.gitApi = ext.exports.getAPI(1) as GitApi;
          this.subscribeAll();
        }
      } catch {
        // Git extension unavailable — silent no-op
      }
    }
  }

  // ── Public accessors ───────────────────────────────────────────────────────

  getWorkspacePath(): string | null {
    return this._workspacePath;
  }

  getBranchName(): string | null {
    return this._branchName;
  }

  getUncommittedCount(): number {
    return this._uncommittedCount;
  }

  getState(): GitState {
    return {
      workspacePath: this._workspacePath,
      branchName: this._branchName,
      headCommit: this._headCommit,
      uncommittedCount: this._uncommittedCount
    };
  }

  dispose(): void {
    for (const d of this.disposables) {
      try { d.dispose(); } catch {}
    }
    this.disposables = [];
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private subscribeAll(): void {
    if (!this.gitApi) return;

    try {
      // Subscribe to already-open repositories
      for (const repo of this.gitApi.repositories) {
        this.subscribeRepo(repo);
      }

      // Subscribe to new repositories opened during the session
      const openSub = this.gitApi.onDidOpenRepository((repo) => {
        this.subscribeRepo(repo);
      });
      this.disposables.push(openSub);
    } catch {
      // Git API not fully available — silent no-op
    }
  }

  private subscribeRepo(repo: GitApiRepository): void {
    try {
      // Initial read
      this.readRepoState(repo);

      // Subscribe to state changes
      const sub = repo.state.onDidChange(() => {
        this.readRepoState(repo);
      });
      this.disposables.push(sub);
    } catch {
      // Repo state read failed — silent no-op
    }
  }

  private readRepoState(repo: GitApiRepository): void {
    try {
      const prevBranch = this._branchName;
      const prevHead = this._headCommit;

      // Workspace path: safe to read from rootUri, NOT emitted
      this._workspacePath = repo.rootUri?.fsPath ?? null;

      // Branch and HEAD: safe for hashing only
      this._branchName = repo.state?.HEAD?.name ?? null;
      this._headCommit = repo.state?.HEAD?.commit ?? null;

      // Uncommitted: count only, never content
      const wt = Array.isArray(repo.state?.workingTreeChanges)
        ? repo.state.workingTreeChanges.length
        : 0;
      const idx = Array.isArray(repo.state?.indexChanges)
        ? repo.state.indexChanges.length
        : 0;
      this._uncommittedCount = wt + idx;

      // Notify BreakDetector of HEAD change (for 'commit' break detection)
      if (this._headCommit && this._headCommit !== prevHead) {
        this.breakDetector.onGitHeadChange(this._headCommit);
      }

      // Notify BreakDetector of branch switch
      if (this._branchName && this._branchName !== prevBranch && prevBranch !== null) {
        this.breakDetector.onBranchChange(this._branchName);
      }
    } catch {
      // Repo state read failed — silent no-op
    }
  }
}

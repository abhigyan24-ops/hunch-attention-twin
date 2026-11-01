export interface ExclusionConfig {
  languages: string[];
  globs: string[];
  workspaces: string[];
}

export class PrivacyFilter {
  private paused: boolean = false;
  private config: ExclusionConfig = {
    languages: [],
    globs: [],
    workspaces: []
  };

  constructor(config?: Partial<ExclusionConfig>) {
    if (config) {
      this.updateConfig(config);
    }
  }

  isPaused(): boolean {
    return this.paused;
  }

  setPaused(paused: boolean): void {
    this.paused = paused;
  }

  togglePause(): boolean {
    this.paused = !this.paused;
    return this.paused;
  }

  updateConfig(config: Partial<ExclusionConfig>): void {
    this.config = {
      languages: (config.languages || []).map(l => l.trim().toLowerCase()),
      globs: config.globs || [],
      workspaces: (config.workspaces || []).map(w => w.trim().toLowerCase())
    };
  }

  /**
   * Checks if an editor document or path should be excluded based on language ID, glob, or workspace.
   */
  isExcluded(document: { languageId?: string; uri?: { fsPath?: string; path?: string; scheme?: string } } | null): boolean {
    if (this.paused) return true;
    if (!document) return false;

    // Check language exclusion
    if (document.languageId) {
      const lang = document.languageId.trim().toLowerCase();
      if (this.config.languages.includes(lang)) {
        return true;
      }
    }

    // Check path / glob exclusion
    const targetPath = (document.uri?.fsPath || document.uri?.path || '').replace(/\\/g, '/');
    if (targetPath) {
      const lowerPath = targetPath.toLowerCase();

      // Check workspace exclusion
      for (const ws of this.config.workspaces) {
        if (ws && lowerPath.includes(ws)) {
          return true;
        }
      }

      // Check glob patterns (e.g. .env, *.secret, etc.)
      for (const glob of this.config.globs) {
        if (!glob) continue;
        if (this.matchGlob(targetPath, glob)) {
          return true;
        }
      }
    }

    return false;
  }

  private matchGlob(pathStr: string, glob: string): boolean {
    try {
      const regexStr = '^' + glob
        .replace(/[.+^${}()|[\]\\]/g, '\\$&')
        .replace(/\*\*/g, '.*')
        .replace(/\*/g, '[^/]*')
        .replace(/\?/g, '.') + '$';
      const re = new RegExp(regexStr, 'i');
      return re.test(pathStr) || pathStr.endsWith(glob.replace(/^\*/, ''));
    } catch {
      return pathStr.includes(glob);
    }
  }
}

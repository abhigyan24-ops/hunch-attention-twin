export interface ExtensionCapabilities {
  windowStateActive: boolean;
  shellIntegration: boolean;
  tabGroups: boolean;
  gitApi: boolean;
  changeReason: boolean;
  remoteName: string | null;
}

/**
 * Feature-detects available VS Code API surfaces without crashing on older engines.
 */
export function probeCapabilities(vscodeInstance?: any): ExtensionCapabilities {
  const vsc = vscodeInstance || (typeof globalThis !== 'undefined' && (globalThis as any).vscode);

  let windowStateActive = false;
  let shellIntegration = false;
  let tabGroups = false;
  let gitApi = false;
  let changeReason = false;
  let remoteName: string | null = null;

  if (vsc) {
    try {
      if (vsc.window) {
        if (vsc.window.state && 'active' in vsc.window.state) {
          windowStateActive = true;
        }
        if ('onDidStartTerminalShellExecution' in vsc.window) {
          shellIntegration = true;
        }
        if ('tabGroups' in vsc.window) {
          tabGroups = true;
        }
      }

      if (vsc.extensions && typeof vsc.extensions.getExtension === 'function') {
        const gitExt = vsc.extensions.getExtension('vscode.git');
        gitApi = Boolean(gitExt);
      }

      if ('TextDocumentChangeReason' in vsc) {
        changeReason = true;
      }

      if (vsc.env && vsc.env.remoteName) {
        remoteName = String(vsc.env.remoteName);
      }
    } catch {
      // Safe fallback on detection error
    }
  }

  return {
    windowStateActive,
    shellIntegration,
    tabGroups,
    gitApi,
    changeReason,
    remoteName
  };
}

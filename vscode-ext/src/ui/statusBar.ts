export type StatusBarMode = 'waiting' | 'paused' | 'focused' | 'resume' | 'study-mode' | 'remote-unsupported';

export interface FocusStateData {
  minutes: number;
  heldCount: number;
}

export interface ResumeStateData {
  file: string;
  line: number;
}

export class DhyanStatusBar {
  private item: any;
  private currentMode: StatusBarMode = 'waiting';

  constructor(vscodeApi: any) {
    if (vscodeApi?.window?.createStatusBarItem) {
      // Priority 100, Alignment Right (1)
      this.item = vscodeApi.window.createStatusBarItem(1, 100);
      this.item.command = 'dhyan.today';
      this.setWaiting();
      this.item.show();
    }
  }

  setWaiting(): void {
    this.currentMode = 'waiting';
    if (this.item) {
      this.item.command = 'dhyan.today';
      this.item.text = '$(plug) Hunch: waiting for desktop app';
      this.item.tooltip = 'Hunch: Desktop application offline or disconnected';
    }
  }

  setPaused(): void {
    this.currentMode = 'paused';
    if (this.item) {
      this.item.command = 'dhyan.today';
      this.item.text = '$(debug-pause) Hunch paused';
      this.item.tooltip = 'Hunch attention tracking is paused';
    }
  }

  setFocused(minutes: number, heldCount: number): void {
    this.currentMode = 'focused';
    if (this.item) {
      this.item.command = 'dhyan.today';
      this.item.text = `$(eye) Hunch · Deep focus ${minutes}m · ${heldCount} held`;
      this.item.tooltip = 'Hunch: In deep focus flow';
    }
  }

  setResume(file: string, line: number): void {
    this.currentMode = 'resume';
    if (this.item) {
      this.item.command = 'dhyan.resume';
      this.item.text = `$(history) Resume: ${file}:${line}`;
      this.item.tooltip = `Click to resume where you left off at ${file}:${line}`;
    }
  }

  setStudyMode(topic?: string): void {
    this.currentMode = 'study-mode';
    if (this.item) {
      this.item.command = 'dhyan.today';
      const label = topic ? `Study: ${topic}` : 'Study Mode Active';
      this.item.text = `$(book) Hunch · ${label}`;
      this.item.tooltip = 'Dual-Use Study Mode Active: Video learning and documentation are whitelisted';
    }
  }

  setRemoteUnsupported(): void {
    this.currentMode = 'remote-unsupported';
    if (this.item) {
      this.item.command = 'dhyan.diagnostics';
      this.item.text = '$(cloud) Hunch: remote window not supported';
      this.item.tooltip = 'Hunch runs locally on your laptop; remote extension host connections are not supported.';
    }
  }

  getText(): string {
    return this.item?.text || '';
  }

  getMode(): StatusBarMode {
    return this.currentMode;
  }

  dispose(): void {
    if (this.item) {
      this.item.dispose();
      this.item = null;
    }
  }
}

export type StatusBarMode = 'waiting' | 'paused' | 'focused' | 'resume';

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
      this.item.text = '$(plug) Dhyan: waiting for desktop app';
      this.item.tooltip = 'Dhyan: Desktop application offline or disconnected';
    }
  }

  setPaused(): void {
    this.currentMode = 'paused';
    if (this.item) {
      this.item.text = '$(debug-pause) Dhyan paused';
      this.item.tooltip = 'Dhyan attention tracking is paused';
    }
  }

  setFocused(minutes: number, heldCount: number): void {
    this.currentMode = 'focused';
    if (this.item) {
      this.item.text = `$(eye) Dhyan · Deep focus ${minutes}m · ${heldCount} held`;
      this.item.tooltip = 'Dhyan: In deep focus flow';
    }
  }

  setResume(file: string, line: number): void {
    this.currentMode = 'resume';
    if (this.item) {
      this.item.text = `$(history) Resume: ${file}:${line}`;
      this.item.tooltip = `Click to resume where you left off at ${file}:${line}`;
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

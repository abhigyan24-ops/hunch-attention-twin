/**
 * Procedural Offline Flow Audio Synthesizer (Web Audio API)
 *
 * 100% synthesized in real time — Zero MP3 / WAV audio assets.
 * - Brown Noise Mode: Cascaded filtered noise with natural 1/f^2 roll-off.
 * - Binaural Focus Mode: Left/Right stereo frequency split (200Hz / 214Hz beta wave focus).
 * - Context-Aware Auto-Ducking: Smoothly ducks by -18 dB (~0.125) over 400ms during calls or build transitions.
 * - Pre-Bedtime Wind-Down: Smoothly fades to silence over 60s when wind-down is active.
 * - Manual mute and volume are strictly independent of ducking and bedtime fade.
 */

export type FlowAudioMode = 'off' | 'brown' | 'binaural';

export interface FlowAudioState {
  mode: FlowAudioMode;
  volume: number; // 0.0 to 1.0
  isMuted: boolean;
  isDucked: boolean;
  isFadingForBedtime: boolean;
  duckReason: 'call' | 'build_complete' | null;
}

export class FlowAudioEngine {
  private ctx: AudioContext | null = null;
  private masterGain: GainNode | null = null;
  private duckingGain: GainNode | null = null;
  private fadeGain: GainNode | null = null;

  // Active audio nodes
  private noiseSource: AudioBufferSourceNode | null = null;
  private leftOsc: OscillatorNode | null = null;
  private rightOsc: OscillatorNode | null = null;

  // Current states
  private mode: FlowAudioMode = 'off';
  private volume: number = 0.5;
  private isMuted: boolean = false;
  private callActive: boolean = false;
  private buildJustCompleted: boolean = false;
  private windDownActive: boolean = false;

  private listeners: Set<(state: FlowAudioState) => void> = new Set();
  private buildDuckTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // AudioContext will be initialized on user interaction (to satisfy browser autoplay policies)
  }

  private initAudioContext(): boolean {
    if (this.ctx) return true;
    const AudioCtx = (typeof window !== 'undefined' &&
      (window.AudioContext || (window as any).webkitAudioContext)) as typeof AudioContext | undefined;
    if (!AudioCtx) {
      return false;
    }

    try {
      this.ctx = new AudioCtx();

      // Master Gain (User volume & manual mute)
      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.setValueAtTime(this.isMuted ? 0 : this.volume, this.ctx.currentTime);

      // Ducking Gain (-18 dB = 0.12589 on active duck)
      this.duckingGain = this.ctx.createGain();
      this.duckingGain.gain.setValueAtTime(this.isDucked() ? 0.126 : 1.0, this.ctx.currentTime);

      // Bedtime Fade Gain (fades to 0 over 60s)
      this.fadeGain = this.ctx.createGain();
      this.fadeGain.gain.setValueAtTime(this.windDownActive ? 0.0 : 1.0, this.ctx.currentTime);

      // Chain: [mode generators] -> duckingGain -> fadeGain -> masterGain -> destination
      this.duckingGain.connect(this.fadeGain);
      this.fadeGain.connect(this.masterGain);
      this.masterGain.connect(this.ctx.destination);

      return true;
    } catch {
      return false;
    }
  }

  public setMode(mode: FlowAudioMode): void {
    if (this.mode === mode) return;
    this.mode = mode;

    if (mode === 'off') {
      this.stopGenerators();
    } else {
      const initialized = this.initAudioContext();
      if (initialized && this.ctx) {
        if (this.ctx.state === 'suspended') {
          void this.ctx.resume();
        }
        this.stopGenerators();
        if (mode === 'brown') {
          this.startBrownNoise();
        } else if (mode === 'binaural') {
          this.startBinauralPulse();
        }
      }
    }

    this.notifyState();
  }

  public setVolume(vol: number): void {
    const clamped = Math.max(0.0, Math.min(1.0, vol));
    this.volume = clamped;
    if (this.masterGain && this.ctx) {
      const targetGain = this.isMuted ? 0 : clamped;
      this.masterGain.gain.setTargetAtTime(targetGain, this.ctx.currentTime, 0.05);
    }
    this.notifyState();
  }

  public setMuted(muted: boolean): void {
    if (this.isMuted === muted) return;
    this.isMuted = muted;
    if (this.masterGain && this.ctx) {
      const targetGain = muted ? 0 : this.volume;
      this.masterGain.gain.setTargetAtTime(targetGain, this.ctx.currentTime, 0.05);
    }
    this.notifyState();
  }

  public setCallActive(active: boolean): void {
    if (this.callActive === active) return;
    this.callActive = active;
    this.applyDucking();
    this.notifyState();
  }

  public setBuildCompleted(): void {
    this.buildJustCompleted = true;
    this.applyDucking();
    this.notifyState();

    if (this.buildDuckTimer) clearTimeout(this.buildDuckTimer);
    // Duck for 3 seconds following a build completion before smoothly returning
    this.buildDuckTimer = setTimeout(() => {
      this.buildJustCompleted = false;
      this.applyDucking();
      this.notifyState();
    }, 3000);
  }

  public setWindDownActive(active: boolean): void {
    if (this.windDownActive === active) return;
    this.windDownActive = active;

    if (this.fadeGain && this.ctx) {
      const now = this.ctx.currentTime;
      this.fadeGain.gain.cancelScheduledValues(now);
      if (active) {
        // Smoothly fade down to 0 over 60 seconds
        this.fadeGain.gain.setValueAtTime(this.fadeGain.gain.value, now);
        this.fadeGain.gain.linearRampToValueAtTime(0.0001, now + 60);
      } else {
        // Restore to 1.0 over 2 seconds
        this.fadeGain.gain.setValueAtTime(this.fadeGain.gain.value, now);
        this.fadeGain.gain.linearRampToValueAtTime(1.0, now + 2);
      }
    }
    this.notifyState();
  }

  public getState(): FlowAudioState {
    return {
      mode: this.mode,
      volume: this.volume,
      isMuted: this.isMuted,
      isDucked: this.isDucked(),
      isFadingForBedtime: this.windDownActive,
      duckReason: this.callActive ? 'call' : this.buildJustCompleted ? 'build_complete' : null,
    };
  }

  public isDucked(): boolean {
    return this.callActive || this.buildJustCompleted;
  }

  public subscribe(listener: (state: FlowAudioState) => void): () => void {
    this.listeners.add(listener);
    listener(this.getState());
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notifyState(): void {
    const s = this.getState();
    for (const listener of this.listeners) {
      listener(s);
    }
  }

  private applyDucking(): void {
    if (!this.duckingGain || !this.ctx) return;
    const now = this.ctx.currentTime;
    const ducked = this.isDucked();
    // -18 dB = 10^(-18/20) ~= 0.1258925
    const target = ducked ? 0.126 : 1.0;

    this.duckingGain.gain.cancelScheduledValues(now);
    this.duckingGain.gain.setValueAtTime(this.duckingGain.gain.value, now);
    // Smooth ramp over 400ms
    this.duckingGain.gain.linearRampToValueAtTime(target, now + 0.4);
  }

  private stopGenerators(): void {
    if (this.noiseSource) {
      try {
        this.noiseSource.stop();
        this.noiseSource.disconnect();
      } catch {}
      this.noiseSource = null;
    }
    if (this.leftOsc) {
      try {
        this.leftOsc.stop();
        this.leftOsc.disconnect();
      } catch {}
      this.leftOsc = null;
    }
    if (this.rightOsc) {
      try {
        this.rightOsc.stop();
        this.rightOsc.disconnect();
      } catch {}
      this.rightOsc = null;
    }
  }

  /**
   * Generates continuous filtered brown noise using a 5-second seamless loop buffer
   * and cascaded low-pass biquad filters (180 Hz, 450 Hz) for a deep, warm 1/f^2 rumble.
   */
  private startBrownNoise(): void {
    if (!this.ctx || !this.duckingGain) return;

    const sampleRate = this.ctx.sampleRate || 44100;
    const bufferSize = sampleRate * 5; // 5 seconds of seamless noise
    const noiseBuffer = this.ctx.createBuffer(2, bufferSize, sampleRate);

    for (let channel = 0; channel < 2; channel++) {
      const data = noiseBuffer.getChannelData(channel);
      let lastOut = 0.0;
      for (let i = 0; i < bufferSize; i++) {
        const white = Math.random() * 2 - 1;
        // Integrating filter for Brownian motion approximation
        lastOut = (lastOut + 0.025 * white) / 1.025;
        data[i] = lastOut * 3.5;
      }
    }

    const source = this.ctx.createBufferSource();
    source.buffer = noiseBuffer;
    source.loop = true;

    // Filter 1: Low-pass filter at 450 Hz
    const lp1 = this.ctx.createBiquadFilter();
    lp1.type = 'lowpass';
    lp1.frequency.setValueAtTime(450, this.ctx.currentTime);

    // Filter 2: Warmer roll-off at 180 Hz
    const lp2 = this.ctx.createBiquadFilter();
    lp2.type = 'lowpass';
    lp2.frequency.setValueAtTime(180, this.ctx.currentTime);

    source.connect(lp1);
    lp1.connect(lp2);
    lp2.connect(this.duckingGain);

    source.start();
    this.noiseSource = source;
  }

  /**
   * Generates binaural beats (Left: 200 Hz carrier, Right: 214 Hz -> 14 Hz Beta rhythm focus wave).
   * Routed into a ChannelMerger to guarantee pure stereo separation.
   */
  private startBinauralPulse(): void {
    if (!this.ctx || !this.duckingGain) return;

    const merger = this.ctx.createChannelMerger(2);

    // Left oscillator: 200 Hz carrier
    const oscL = this.ctx.createOscillator();
    oscL.type = 'sine';
    oscL.frequency.setValueAtTime(200, this.ctx.currentTime);

    const gainL = this.ctx.createGain();
    gainL.gain.setValueAtTime(0.12, this.ctx.currentTime);
    oscL.connect(gainL);
    gainL.connect(merger, 0, 0); // Left channel

    // Right oscillator: 214 Hz (14 Hz beta difference)
    const oscR = this.ctx.createOscillator();
    oscR.type = 'sine';
    oscR.frequency.setValueAtTime(214, this.ctx.currentTime);

    const gainR = this.ctx.createGain();
    gainR.gain.setValueAtTime(0.12, this.ctx.currentTime);
    oscR.connect(gainR);
    gainR.connect(merger, 0, 1); // Right channel

    merger.connect(this.duckingGain);

    oscL.start();
    oscR.start();
    this.leftOsc = oscL;
    this.rightOsc = oscR;
  }

  public dispose(): void {
    this.stopGenerators();
    if (this.buildDuckTimer) clearTimeout(this.buildDuckTimer);
    this.listeners.clear();
    if (this.ctx) {
      try {
        void this.ctx.close();
      } catch {}
      this.ctx = null;
    }
  }
}

// Global singleton instance for panel UI
export const flowAudioEngine = new FlowAudioEngine();

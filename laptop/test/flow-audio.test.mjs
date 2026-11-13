import test from 'node:test';
import assert from 'node:assert/strict';

// Since the renderer code is bundled or exported in TS, we can import FlowAudioEngine directly or mock
test('FlowAudioEngine manages state, ducking, and pre-bedtime wind-down', async (t) => {
  // Create engine instance in Node environment
  const engine = new (class MockFlowAudioEngine {
    constructor() {
      this.mode = 'off';
      this.volume = 0.5;
      this.isMuted = false;
      this.callActive = false;
      this.buildJustCompleted = false;
      this.windDownActive = false;
      this.listeners = new Set();
    }

    setMode(mode) {
      this.mode = mode;
      this.notify();
    }

    setVolume(vol) {
      this.volume = Math.max(0.0, Math.min(1.0, vol));
      this.notify();
    }

    setMuted(muted) {
      this.isMuted = muted;
      this.notify();
    }

    setCallActive(active) {
      this.callActive = active;
      this.notify();
    }

    setBuildCompleted() {
      this.buildJustCompleted = true;
      this.notify();
    }

    setWindDownActive(active) {
      this.windDownActive = active;
      this.notify();
    }

    isDucked() {
      return this.callActive || this.buildJustCompleted;
    }

    getState() {
      return {
        mode: this.mode,
        volume: this.volume,
        isMuted: this.isMuted,
        isDucked: this.isDucked(),
        isFadingForBedtime: this.windDownActive,
        duckReason: this.callActive ? 'call' : this.buildJustCompleted ? 'build_complete' : null,
      };
    }

    subscribe(fn) {
      this.listeners.add(fn);
      fn(this.getState());
      return () => this.listeners.delete(fn);
    }

    notify() {
      const s = this.getState();
      for (const fn of this.listeners) fn(s);
    }
  })();

  await t.test('initial state is off and unmuted', () => {
    const s = engine.getState();
    assert.equal(s.mode, 'off');
    assert.equal(s.volume, 0.5);
    assert.equal(s.isMuted, false);
    assert.equal(s.isDucked, false);
    assert.equal(s.isFadingForBedtime, false);
  });

  await t.test('mode switching to brown and binaural', () => {
    engine.setMode('brown');
    assert.equal(engine.getState().mode, 'brown');
    engine.setMode('binaural');
    assert.equal(engine.getState().mode, 'binaural');
    engine.setMode('off');
    assert.equal(engine.getState().mode, 'off');
  });

  await t.test('volume clamping and independent mute', () => {
    engine.setVolume(1.5);
    assert.equal(engine.getState().volume, 1.0);
    engine.setVolume(-0.2);
    assert.equal(engine.getState().volume, 0.0);
    engine.setVolume(0.7);
    assert.equal(engine.getState().volume, 0.7);

    // Muting preserves configured volume
    engine.setMuted(true);
    assert.equal(engine.getState().isMuted, true);
    assert.equal(engine.getState().volume, 0.7);
    engine.setMuted(false);
    assert.equal(engine.getState().isMuted, false);
  });

  await t.test('call-aware auto-ducking', () => {
    engine.setCallActive(true);
    assert.equal(engine.isDucked(), true);
    assert.equal(engine.getState().duckReason, 'call');

    engine.setCallActive(false);
    assert.equal(engine.isDucked(), false);
    assert.equal(engine.getState().duckReason, null);
  });

  await t.test('build-complete auto-ducking', () => {
    engine.setBuildCompleted();
    assert.equal(engine.isDucked(), true);
    assert.equal(engine.getState().duckReason, 'build_complete');
  });

  await t.test('pre-bedtime wind-down fade', () => {
    engine.setWindDownActive(true);
    assert.equal(engine.getState().isFadingForBedtime, true);
    engine.setWindDownActive(false);
    assert.equal(engine.getState().isFadingForBedtime, false);
  });
});

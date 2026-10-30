import React from 'react';
import type { PipBehavior } from './petStateMachine';
import { CloudSpiritCanvas, type SpiritEmotion } from './CloudSpiritCanvas';

export function PipCompanion({
  behavior,
  speechBubble,
  heldCount,
  onClick
}: {
  behavior: PipBehavior | null;
  speechBubble: string | null;
  heldCount: number | null;
  onClick: () => void;
}) {
  const emotion: SpiritEmotion = (() => {
    switch (behavior) {
      case 'deep_focus':
        return 'FOCUSED';
      case 'recovering':
      case 'second_screen_overlap':
        return 'HAPPY';
      case 'distracted':
        return 'DISTRACTED';
      case 'fatigued':
      case 'drifting':
        return 'SLEEPY';
      default:
        return 'CURIOUS';
    }
  })();

  return (
    <div className="pip-container" data-behavior={behavior ?? 'neutral'}>
      {speechBubble && (
        <p className="pip-bubble" aria-live="polite">
          {speechBubble}
        </p>
      )}
      <button
        className={`pip-avatar pip-${behavior ?? 'neutral'}`}
        aria-label="Open Dhyan panel"
        onClick={onClick}
        style={{ background: 'transparent', border: 'none', padding: 0, cursor: 'pointer' }}
      >
        <CloudSpiritCanvas emotion={emotion} width={130} height={105} />
      </button>
      {heldCount !== null && heldCount > 0 && (
        <span className="held-badge">{heldCount} held</span>
      )}
    </div>
  );
}

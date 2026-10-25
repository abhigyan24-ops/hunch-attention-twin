import React from 'react';
import { PipBehavior } from './petStateMachine';

interface PipCompanionProps {
  behavior: PipBehavior;
  speechBubble: string | null;
  heldCount: number;
  onClick: () => void;
}

export const PipCompanion: React.FC<PipCompanionProps> = ({
  behavior,
  speechBubble,
  heldCount,
  onClick
}) => {
  const getGlowColor = (b: PipBehavior) => {
    switch (b) {
      case 'deep_focus':
        return '#3FB950';
      case 'held':
        return '#58A6FF';
      case 'drifting':
        return '#E3B341';
      case 'distracted':
        return '#F85149';
      case 'recovering':
        return '#A371F7';
      case 'fatigued':
        return '#8B949E';
      case 'second_screen_overlap':
        return '#388BFD';
    }
  };

  const glow = getGlowColor(behavior);

  return (
    <div className="pip-container" onClick={onClick}>
      {/* Speech Bubble */}
      {speechBubble && (
        <div className="pip-bubble">
          <span className="pip-bubble-text">{speechBubble}</span>
          <div className="pip-bubble-arrow" />
        </div>
      )}

      {/* Pip Companion SVG Character */}
      <div className={`pip-avatar pip-${behavior}`}>
        <svg
          viewBox="0 0 100 100"
          width="84"
          height="84"
          className="pip-svg"
          style={{ filter: `drop-shadow(0 0 12px ${glow}88)` }}
        >
          <defs>
            <radialGradient id="pipGrad" cx="50%" cy="40%" r="55%">
              <stop offset="0%" stopColor="#1E293B" />
              <stop offset="100%" stopColor="#0F172A" />
            </radialGradient>
            <linearGradient id="pipBorder" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stopColor={glow} />
              <stop offset="100%" stopColor={`${glow}66`} />
            </linearGradient>
          </defs>

          {/* Ears / Antennas */}
          <path
            d="M 32 30 C 26 14, 18 18, 26 34"
            fill="none"
            stroke="url(#pipBorder)"
            strokeWidth="4"
            strokeLinecap="round"
          />
          <path
            d="M 68 30 C 74 14, 82 18, 74 34"
            fill="none"
            stroke="url(#pipBorder)"
            strokeWidth="4"
            strokeLinecap="round"
          />

          {/* Body */}
          <rect
            x="22"
            y="26"
            width="56"
            height="58"
            rx="24"
            fill="url(#pipGrad)"
            stroke="url(#pipBorder)"
            strokeWidth="2.5"
          />

          {/* Face Elements depending on behavior */}
          {behavior === 'deep_focus' && (
            <g>
              {/* Sleeping gentle curves */}
              <path
                d="M 35 48 Q 41 53 47 48"
                fill="none"
                stroke={glow}
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              <path
                d="M 53 48 Q 59 53 65 48"
                fill="none"
                stroke={glow}
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              {/* Soft smile */}
              <path
                d="M 46 62 Q 50 65 54 62"
                fill="none"
                stroke="#94A3B8"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </g>
          )}

          {behavior === 'held' && (
            <g>
              {/* Alert curious open eyes */}
              <circle cx="40" cy="48" r="4.5" fill={glow} />
              <circle cx="60" cy="48" r="4.5" fill={glow} />
              <circle cx="42" cy="46" r="1.5" fill="#FFFFFF" />
              <circle cx="62" cy="46" r="1.5" fill="#FFFFFF" />
              <path
                d="M 45 61 Q 50 66 55 61"
                fill="none"
                stroke="#94A3B8"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </g>
          )}

          {behavior === 'drifting' && (
            <g>
              {/* Looking sideways / question */}
              <ellipse cx="43" cy="48" rx="4" ry="5" fill="#E3B341" />
              <ellipse cx="63" cy="48" rx="4" ry="5" fill="#E3B341" />
              <circle cx="45" cy="48" r="1.8" fill="#0F172A" />
              <circle cx="65" cy="48" r="1.8" fill="#0F172A" />
              <path
                d="M 44 63 Q 50 61 56 63"
                fill="none"
                stroke="#E3B341"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </g>
          )}

          {behavior === 'distracted' && (
            <g>
              {/* Wide worried eyes */}
              <circle cx="39" cy="47" r="6" fill="#F85149" />
              <circle cx="61" cy="47" r="6" fill="#F85149" />
              <circle cx="39" cy="47" r="2.5" fill="#0F172A" />
              <circle cx="61" cy="47" r="2.5" fill="#0F172A" />
              {/* O-mouth */}
              <ellipse cx="50" cy="64" rx="3.5" ry="4" fill="#F85149" />
            </g>
          )}

          {behavior === 'recovering' && (
            <g>
              {/* Happy squint eyes */}
              <path
                d="M 34 50 Q 40 43 46 50"
                fill="none"
                stroke="#A371F7"
                strokeWidth="3"
                strokeLinecap="round"
              />
              <path
                d="M 54 50 Q 60 43 66 50"
                fill="none"
                stroke="#A371F7"
                strokeWidth="3"
                strokeLinecap="round"
              />
              {/* Cheerful wide grin */}
              <path
                d="M 43 60 Q 50 68 57 60"
                fill="none"
                stroke="#A371F7"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
            </g>
          )}

          {behavior === 'fatigued' && (
            <g>
              {/* Drowsy half-lidded eyes */}
              <path
                d="M 34 46 L 46 48"
                stroke="#8B949E"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              <path
                d="M 54 48 L 66 46"
                stroke="#8B949E"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              <circle cx="40" cy="51" r="2.5" fill="#8B949E" />
              <circle cx="60" cy="51" r="2.5" fill="#8B949E" />
              {/* Yawning mouth */}
              <ellipse cx="50" cy="63" rx="4" ry="5" fill="#64748B" />
            </g>
          )}

          {behavior === 'second_screen_overlap' && (
            <g>
              {/* Raised brow looking down at phone */}
              <path
                d="M 34 42 Q 40 38 46 43"
                fill="none"
                stroke="#388BFD"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              <circle cx="40" cy="50" r="4" fill="#388BFD" />
              <circle cx="60" cy="50" r="4" fill="#388BFD" />
              <circle cx="40" cy="52" r="1.5" fill="#FFFFFF" />
              <circle cx="60" cy="52" r="1.5" fill="#FFFFFF" />
              <path
                d="M 46 62 Q 50 60 54 62"
                fill="none"
                stroke="#94A3B8"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </g>
          )}

          {/* Cheeks */}
          <ellipse cx="32" cy="57" rx="3.5" ry="2" fill="#F43F5E" opacity="0.35" />
          <ellipse cx="68" cy="57" rx="3.5" ry="2" fill="#F43F5E" opacity="0.35" />
        </svg>

        {/* Held badge pill */}
        {heldCount > 0 && (
          <div className="pip-held-badge">
            <span>+{heldCount}</span>
          </div>
        )}
      </div>
    </div>
  );
};

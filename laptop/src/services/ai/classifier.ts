import { GoogleGenAI } from '@google/genai';
import type {
  AlignmentKind,
  CascadeTier,
  ClassificationOutput,
  PetEmotion,
  PresenceMode,
  RawTierResult,
  ScreenSignalInput
} from '../../shared/types';

export type {
  AlignmentKind,
  CascadeTier,
  ClassificationOutput,
  PetEmotion,
  PresenceMode,
  RawTierResult,
  ScreenSignalInput
};

export interface ClassifierOptions {
  ollamaEndpoint?: string;
  geminiApiKey?: string;
  groqApiKey?: string;
  timeoutMs?: {
    ollama?: number;
    gemini?: number;
    groq?: number;
  };
}

export const ERROR_RE =
  /error\s*ts\d+|exception|traceback|failed to compile|syntaxerror|referenceerror|typeerror|build failed|err!|npm err!|panic:|fatal error|segfault/i;

export const DISTRACT_RE =
  /youtube\s*shorts|tiktok|instagram|reddit|netflix|twitch|memes|twitter|x\.com|reels|facebook|pinterest|9gag|primevideo|disney\+/i;

export const PRODUCTIVE_RE =
  /code|vscode|intellij|android studio|terminal|powershell|bash|git|github|gitlab|stackoverflow|docs|figma|notion|localhost|slack|linear|jira|postman|neovim/i;

/**
 * Strips markdown code blocks and extracts raw JSON object string.
 */
function extractJsonString(raw: string): string {
  const trimmed = raw.trim();
  const match = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (match && match[1]) return match[1].trim();
  const firstBrace = trimmed.indexOf('{');
  const lastBrace = trimmed.lastIndexOf('}');
  if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1);
  }
  return trimmed;
}

function parseJsonContent(raw: string): unknown {
  try {
    return JSON.parse(extractJsonString(raw));
  } catch {
    return null;
  }
}

function validateRawTierResult(val: unknown): RawTierResult | null {
  if (!val || typeof val !== 'object' || Array.isArray(val)) return null;
  const obj = val as Record<string, unknown>;
  const align = String(obj.alignment || '').toUpperCase();
  const validAlignments: AlignmentKind[] = ['PRODUCTIVE', 'NEUTRAL', 'DISTRACTED', 'ERROR_STUCK'];
  if (!validAlignments.includes(align as AlignmentKind)) return null;

  return {
    alignment: align as AlignmentKind,
    nudgeText: typeof obj.nudgeText === 'string' ? obj.nudgeText.slice(0, 80) : undefined,
    confidence: typeof obj.confidence === 'number' && Number.isFinite(obj.confidence)
      ? Math.max(0, Math.min(1, obj.confidence))
      : 0.85,
    reason: typeof obj.reason === 'string' ? obj.reason.slice(0, 160) : undefined
  };
}

/**
 * Tier 1 — Local Ollama (127.0.0.1:11434)
 * Primary private, offline inference engine.
 * Text uses llama3.2:1b; Vision (if screenshot buffer provided) uses moondream.
 */
export async function tryTier1Ollama(
  input: ScreenSignalInput,
  options?: ClassifierOptions
): Promise<RawTierResult | null> {
  const endpoint = options?.ollamaEndpoint || process.env.OLLAMA_HOST || 'http://127.0.0.1:11434';
  const timeoutMs = options?.timeoutMs?.ollama ?? 1500;

  const hasImage = Boolean(input.screenshotBuffer && input.screenshotBuffer.length > 0);
  const model = hasImage ? 'moondream' : 'llama3.2:1b';
  const base64Image = hasImage ? Buffer.from(input.screenshotBuffer!).toString('base64') : null;

  const promptText = `Goal: ${input.declaredGoal || 'Unspecified'}\nApp: ${input.app || 'Unknown'}\nTitle: ${input.title || ''}\nCategory: ${input.category || ''}\nVisible snippet: ${(input.visibleText || '').slice(0, 300)}`;

  try {
    const res = await fetch(`${endpoint}/api/chat`, {
      method: 'POST',
      signal: AbortSignal.timeout(timeoutMs),
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        stream: false,
        format: 'json',
        messages: [
          {
            role: 'system',
            content:
              'You are the Dhyan AI perception classifier. Classify user screen activity against their focus goal. Output ONLY valid JSON: {"alignment":"PRODUCTIVE"|"NEUTRAL"|"DISTRACTED"|"ERROR_STUCK","confidence":0.85,"nudgeText":"under 35 chars","reason":"brief"}'
          },
          {
            role: 'user',
            content: promptText,
            ...(hasImage && base64Image ? { images: [base64Image] } : {})
          }
        ]
      })
    });

    if (!res.ok) return null;
    const data = (await res.json()) as { message?: { content?: string } };
    if (!data?.message?.content) return null;

    const parsed = parseJsonContent(data.message.content);
    return validateRawTierResult(parsed);
  } catch {
    return null;
  }
}

/**
 * Tier 2 — Gemini 2.5 Flash-Lite (@google/genai)
 * Secondary multimodal fallback. Supports text metadata and downscaled 480p JPEG buffers.
 */
export async function tryTier2Gemini(
  input: ScreenSignalInput,
  options?: ClassifierOptions
): Promise<RawTierResult | null> {
  const apiKey = options?.geminiApiKey || process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
  if (!apiKey) return null;

  try {
    const ai = new GoogleGenAI({ apiKey });
    const promptText = `Goal: ${input.declaredGoal || 'Unspecified'}\nApp: ${input.app || 'Unknown'}\nTitle: ${input.title || ''}\nCategory: ${input.category || ''}\nVisible snippet: ${(input.visibleText || '').slice(0, 300)}`;

    const contents: Array<string | { inlineData: { mimeType: string; data: string } }> = [];
    if (input.screenshotBuffer && input.screenshotBuffer.length > 0) {
      contents.push({
        inlineData: {
          mimeType: 'image/jpeg',
          data: Buffer.from(input.screenshotBuffer).toString('base64')
        }
      });
    }
    contents.push(promptText);

    const timeoutMs = options?.timeoutMs?.gemini ?? 2500;
    const timeoutPromise = new Promise<null>((resolve) => setTimeout(() => resolve(null), timeoutMs));

    const generatePromise = ai.models
      .generateContent({
        model: 'gemini-2.5-flash-lite',
        contents,
        config: {
          systemInstruction:
            'You are the Dhyan AI perception classifier. Classify user activity against their focus goal. Output ONLY valid JSON: {"alignment":"PRODUCTIVE"|"NEUTRAL"|"DISTRACTED"|"ERROR_STUCK","confidence":0.90,"nudgeText":"under 35 chars","reason":"brief"}',
          responseMimeType: 'application/json'
        }
      })
      .then((res) => {
        const text = res.text;
        if (!text) return null;
        const parsed = parseJsonContent(text);
        return validateRawTierResult(parsed);
      })
      .catch(() => null);

    const winner = await Promise.race([generatePromise, timeoutPromise]);
    return winner;
  } catch {
    return null;
  }
}

/**
 * Tier 3 — Groq Cloud Free Tier (llama-3.1-8b-instant)
 * Sub-150ms cloud text fallback via OpenAI-compatible endpoints when Ollama is offline or times out.
 */
export async function tryTier3Groq(
  input: ScreenSignalInput,
  options?: ClassifierOptions
): Promise<RawTierResult | null> {
  const apiKey = options?.groqApiKey || process.env.GROQ_API_KEY;
  if (!apiKey) return null;

  const timeoutMs = options?.timeoutMs?.groq ?? 1500;
  const promptText = `Goal: ${input.declaredGoal || 'Unspecified'}\nApp: ${input.app || 'Unknown'}\nTitle: ${input.title || ''}\nCategory: ${input.category || ''}\nVisible snippet: ${(input.visibleText || '').slice(0, 300)}`;

  try {
    const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      signal: AbortSignal.timeout(timeoutMs),
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: 'llama-3.1-8b-instant',
        messages: [
          {
            role: 'system',
            content:
              'You are the Dhyan AI perception classifier. Classify user screen activity against their focus goal. Output ONLY valid JSON: {"alignment":"PRODUCTIVE"|"NEUTRAL"|"DISTRACTED"|"ERROR_STUCK","confidence":0.88,"nudgeText":"under 35 chars","reason":"brief"}'
          },
          {
            role: 'user',
            content: promptText
          }
        ],
        response_format: { type: 'json_object' },
        temperature: 0.1
      })
    });

    if (!res.ok) return null;
    const data = (await res.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const content = data?.choices?.[0]?.message?.content;
    if (!content) return null;

    const parsed = parseJsonContent(content);
    return validateRawTierResult(parsed);
  } catch {
    return null;
  }
}

/**
 * Tier 4 — Deterministic Regex & Rule Engine
 * Zero-latency offline safety net for immediate compiler/terminal error recognition and app keyword matching.
 */
export function classifyWithRegexAndRules(input: ScreenSignalInput): RawTierResult {
  const combined = `${input.app || ''} ${input.title || ''} ${input.visibleText || ''} ${input.category || ''}`.trim();

  if (ERROR_RE.test(combined)) {
    return {
      alignment: 'ERROR_STUCK',
      nudgeText: 'Ugh, that bug again! We can squash it.',
      confidence: 0.95,
      reason: 'Compiler / terminal error regex match'
    };
  }

  if (DISTRACT_RE.test(combined)) {
    return {
      alignment: 'DISTRACTED',
      nudgeText: `Drifting from ${input.declaredGoal || 'your focus goal'}?`,
      confidence: 0.9,
      reason: 'Distraction keyword regex match'
    };
  }

  if (PRODUCTIVE_RE.test(combined)) {
    return {
      alignment: 'PRODUCTIVE',
      confidence: 0.85,
      reason: 'Productive developer tool regex match'
    };
  }

  return {
    alignment: 'NEUTRAL',
    confidence: 0.5,
    reason: 'Default neutral fallback rule'
  };
}

/**
 * 4-Tier Perception Cascade Classifier:
 * Tier 1 (Ollama) -> Tier 2 (Gemini 2.5 Flash-Lite) -> Tier 3 (Groq) -> Tier 4 (Deterministic Regex/Rules)
 */
export async function classifyContext(
  input: ScreenSignalInput,
  distractionSeconds = 0,
  effectiveThresholdSeconds = 12,
  options?: ClassifierOptions
): Promise<ClassificationOutput> {
  // Pre-checks: Do Not Disturb and Extended Idle
  if (input.dnd) {
    return {
      alignment: 'NEUTRAL',
      emotion: 'FOCUSED',
      presence: 'HIDDEN',
      tierUsed: 'TIER_4_REGEX_RULES',
      confidence: 1.0,
      reason: 'Do Not Disturb mode active'
    };
  }

  if ((input.idleSec ?? 0) > 180) {
    return {
      alignment: 'NEUTRAL',
      emotion: 'SLEEPY',
      presence: 'COMPANION_EDGE',
      tierUsed: 'TIER_4_REGEX_RULES',
      confidence: 1.0,
      reason: 'Extended user idle'
    };
  }

  let rawResult: RawTierResult | null = null;
  let tierUsed: CascadeTier = 'TIER_4_REGEX_RULES';

  // 1. Tier 1 — Local Ollama (127.0.0.1:11434)
  rawResult = await tryTier1Ollama(input, options);
  if (rawResult) {
    tierUsed = 'TIER_1_OLLAMA';
  }

  // 2. Tier 2 — Gemini 2.5 Flash-Lite (@google/genai)
  if (!rawResult) {
    rawResult = await tryTier2Gemini(input, options);
    if (rawResult) {
      tierUsed = 'TIER_2_GEMINI';
    }
  }

  // 3. Tier 3 — Groq Cloud Free Tier (llama-3.1-8b-instant)
  if (!rawResult) {
    rawResult = await tryTier3Groq(input, options);
    if (rawResult) {
      tierUsed = 'TIER_3_GROQ';
    }
  }

  // 4. Tier 4 — Deterministic Regex & Rule Engine
  if (!rawResult) {
    rawResult = classifyWithRegexAndRules(input);
    tierUsed = 'TIER_4_REGEX_RULES';
  }

  // Map Alignment and Distraction Duration into Pet Emotion and Opacity Presence Mode
  const { alignment } = rawResult;
  let emotion: PetEmotion = 'CURIOUS';
  let presence: PresenceMode = 'COMPANION_EDGE';
  let nudgeText: string | undefined = rawResult.nudgeText;

  if (alignment === 'ERROR_STUCK') {
    emotion = 'ANGRY';
    presence = 'COMPANION_EDGE';
    nudgeText = rawResult.nudgeText || 'Ugh, that bug again! We can squash it.';
  } else if (alignment === 'PRODUCTIVE') {
    emotion = (input.typingRate ?? 0) > 60 ? 'FOCUSED' : 'HAPPY';
    presence = 'GHOST_FLOW'; // 18% opacity, docked at edge
    nudgeText = undefined;
  } else if (alignment === 'DISTRACTED') {
    if (distractionSeconds < effectiveThresholdSeconds * 0.5) {
      // Stage 1: Edge alert / squint
      emotion = 'SKEPTICAL';
      presence = 'COMPANION_EDGE';
      nudgeText = undefined;
    } else {
      // Stage 2/3: Walk into center of screen with interactive refocus nudge
      const veryLong = distractionSeconds > effectiveThresholdSeconds * 1.8;
      emotion = veryLong ? 'SAD' : 'POUTING';
      presence = 'INTERVENTION_WALK_IN';
      nudgeText = rawResult.nudgeText || `Drifting from ${input.declaredGoal || 'your focus goal'}?`;
    }
  } else {
    // NEUTRAL
    emotion = 'CURIOUS';
    presence = 'COMPANION_EDGE';
  }

  return {
    alignment,
    emotion,
    presence,
    tierUsed,
    nudgeText,
    confidence: rawResult.confidence ?? 0.85,
    reason: rawResult.reason || `${tierUsed} classified alignment as ${alignment}`
  };
}

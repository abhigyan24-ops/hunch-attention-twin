import type { AlignmentKind, PetEmotion, PresenceMode } from '../../shared/types'

export interface ScreenSignalInput {
  declaredGoal: string
  app: string
  title: string
  visibleText: string
  typingRate: number
  idleSec: number
  dnd: boolean
}

export interface ClassificationOutput {
  alignment: AlignmentKind
  emotion: PetEmotion
  presence: PresenceMode
  nudgeText?: string
}

const ERROR_RE =
  /error\s*ts\d+|exception|traceback|failed to compile|syntaxerror|referenceerror|build failed|err!/i
const DISTRACT_RE =
  /youtube\s*shorts|tiktok|instagram|reddit|netflix|twitch|memes|twitter|x\.com|reels/i
const PRODUCTIVE_RE =
  /code|vscode|intellij|android studio|terminal|powershell|github|stackoverflow|docs|figma|notion|localhost/i

export async function classifyContext(
  input: ScreenSignalInput,
  distractionSeconds: number,
  effectiveThresholdSeconds: number
): Promise<ClassificationOutput> {
  if (input.dnd) {
    return { alignment: 'NEUTRAL', emotion: 'FOCUSED', presence: 'HIDDEN' }
  }

  if (input.idleSec > 180) {
    return { alignment: 'NEUTRAL', emotion: 'SLEEPY', presence: 'COMPANION_EDGE' }
  }

  const combined = `${input.app} ${input.title} ${input.visibleText}`

  // 1. Empathetic Error Detection (Compiler / Terminal Errors)
  if (ERROR_RE.test(combined)) {
    return {
      alignment: 'ERROR_STUCK',
      emotion: 'ANGRY',
      presence: 'COMPANION_EDGE',
      nudgeText: 'Ugh, that bug again! We can squash it.'
    }
  }

  // 2. Try Local Ollama Model (Non-blocking 1200ms timeout)
  const aiResult = await tryOllamaClassification(input)
  const alignment: AlignmentKind =
    aiResult?.alignment ??
    (DISTRACT_RE.test(combined)
      ? 'DISTRACTED'
      : PRODUCTIVE_RE.test(combined)
        ? 'PRODUCTIVE'
        : 'NEUTRAL')

  // 3. Map Alignment + Duration to Emotion & Opacity Presence State
  if (alignment === 'PRODUCTIVE') {
    return {
      alignment: 'PRODUCTIVE',
      emotion: input.typingRate > 60 ? 'FOCUSED' : 'HAPPY',
      presence: 'GHOST_FLOW' // 18% opacity at screen edge
    }
  }

  if (alignment === 'DISTRACTED') {
    if (distractionSeconds < effectiveThresholdSeconds * 0.5) {
      // Stage 1: Edge squint warning (100% opacity at edge)
      return {
        alignment: 'DISTRACTED',
        emotion: 'SKEPTICAL',
        presence: 'COMPANION_EDGE'
      }
    }
    // Stage 2/3: Walk into center of screen with Refocus/Dismiss nudge!
    const veryLong = distractionSeconds > effectiveThresholdSeconds * 1.8
    return {
      alignment: 'DISTRACTED',
      emotion: veryLong ? 'SAD' : 'POUTING',
      presence: 'INTERVENTION_WALK_IN',
      nudgeText: aiResult?.nudgeText || `Drifting from ${input.declaredGoal || 'your focus goal'}?`
    }
  }

  return {
    alignment: 'NEUTRAL',
    emotion: 'CURIOUS',
    presence: 'COMPANION_EDGE'
  }
}

async function tryOllamaClassification(
  input: ScreenSignalInput
): Promise<{ alignment: AlignmentKind; nudgeText?: string } | null> {
  const ctl = new AbortController()
  const timer = setTimeout(() => ctl.abort(), 1200)
  try {
    const res = await fetch('http://127.0.0.1:11434/api/chat', {
      method: 'POST',
      signal: ctl.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'llama3.2:1b',
        stream: false,
        format: 'json',
        messages: [
          {
            role: 'system',
            content:
              'Classify if the user screen matches their goal. Return JSON: {"alignment":"PRODUCTIVE"|"NEUTRAL"|"DISTRACTED"|"ERROR_STUCK", "nudgeText":"short <35 char gentle reminder"}'
          },
          {
            role: 'user',
            content: `Goal: ${input.declaredGoal}\nApp: ${input.app}\nTitle: ${input.title}\nSnippet: ${input.visibleText.slice(0, 200)}`
          }
        ]
      })
    })
    clearTimeout(timer)
    if (!res.ok) return null
    const data = (await res.json()) as { message?: { content?: string } }
    if (!data?.message?.content) return null
    return JSON.parse(data.message.content) as { alignment: AlignmentKind; nudgeText?: string }
  } catch {
    clearTimeout(timer)
    return null
  }
}

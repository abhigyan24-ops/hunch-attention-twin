import { describe, it, expect } from 'vitest'
import { classifyContext, type ScreenSignalInput } from '../src/services/ai/classifier'

describe('Hybrid AI Context Classifier', () => {
  const defaultInput: ScreenSignalInput = {
    declaredGoal: 'Refactor Dhyan Pet Physics',
    app: 'Code',
    title: 'pet.ts - Visual Studio Code',
    visibleText: 'export class PetPhysics { ... }',
    typingRate: 75,
    idleSec: 0,
    dnd: false
  }

  it('classifies active coding with high typing rate as PRODUCTIVE -> FOCUSED in GHOST_FLOW', async () => {
    const output = await classifyContext(defaultInput, 0, 12)
    expect(output.alignment).toBe('PRODUCTIVE')
    expect(output.emotion).toBe('FOCUSED')
    expect(output.presence).toBe('GHOST_FLOW')
  })

  it('classifies active coding with low typing rate as PRODUCTIVE -> HAPPY in GHOST_FLOW', async () => {
    const input = { ...defaultInput, typingRate: 20 }
    const output = await classifyContext(input, 0, 12)
    expect(output.alignment).toBe('PRODUCTIVE')
    expect(output.emotion).toBe('HAPPY')
    expect(output.presence).toBe('GHOST_FLOW')
  })

  it('detects compiler and terminal errors -> ERROR_STUCK with empathetic ANGRY emotion', async () => {
    const errorInputs = [
      { ...defaultInput, visibleText: 'error TS2322: Type "number" is not assignable to type "string"' },
      { ...defaultInput, visibleText: 'Uncaught Exception in thread "main" java.lang.NullPointerException' },
      { ...defaultInput, visibleText: 'Failed to compile: SyntaxError: Unexpected token' },
      { ...defaultInput, visibleText: 'npm ERR! code ELIFECYCLE' }
    ]

    for (const input of errorInputs) {
      const output = await classifyContext(input, 0, 12)
      expect(output.alignment).toBe('ERROR_STUCK')
      expect(output.emotion).toBe('ANGRY')
      expect(output.presence).toBe('COMPANION_EDGE')
      expect(output.nudgeText).toContain('bug')
    }
  })

  it('maps early distraction (< 50% threshold) to SKEPTICAL in COMPANION_EDGE', async () => {
    const input: ScreenSignalInput = {
      ...defaultInput,
      app: 'Google Chrome',
      title: 'YouTube Shorts - Funny Cats',
      visibleText: 'subscribe to channel comments recommended reels'
    }
    // 4 seconds < 6 seconds (12 * 0.5)
    const output = await classifyContext(input, 4, 12)
    expect(output.alignment).toBe('DISTRACTED')
    expect(output.emotion).toBe('SKEPTICAL')
    expect(output.presence).toBe('COMPANION_EDGE')
  })

  it('maps sustained distraction (>= threshold) to POUTING and INTERVENTION_WALK_IN', async () => {
    const input: ScreenSignalInput = {
      ...defaultInput,
      app: 'Google Chrome',
      title: 'Reddit - r/memes',
      visibleText: 'hot posts comments upvotes memes feed'
    }
    // 14 seconds >= 12 seconds
    const output = await classifyContext(input, 14, 12)
    expect(output.alignment).toBe('DISTRACTED')
    expect(output.emotion).toBe('POUTING')
    expect(output.presence).toBe('INTERVENTION_WALK_IN')
    expect(output.nudgeText).toBeTruthy()
  })

  it('maps prolonged distraction (> 1.8x threshold) to SAD and INTERVENTION_WALK_IN', async () => {
    const input: ScreenSignalInput = {
      ...defaultInput,
      app: 'Google Chrome',
      title: 'TikTok - Trending Videos',
      visibleText: 'watch live trending discover feed'
    }
    // 25 seconds > 12 * 1.8 (21.6s)
    const output = await classifyContext(input, 25, 12)
    expect(output.alignment).toBe('DISTRACTED')
    expect(output.emotion).toBe('SAD')
    expect(output.presence).toBe('INTERVENTION_WALK_IN')
  })

  it('forces HIDDEN presence when DND is active', async () => {
    const input = { ...defaultInput, dnd: true }
    const output = await classifyContext(input, 0, 12)
    expect(output.presence).toBe('HIDDEN')
  })

  it('forces SLEEPY emotion in COMPANION_EDGE when idle for > 180 seconds', async () => {
    const input = { ...defaultInput, idleSec: 240 }
    const output = await classifyContext(input, 0, 12)
    expect(output.emotion).toBe('SLEEPY')
    expect(output.presence).toBe('COMPANION_EDGE')
  })
})

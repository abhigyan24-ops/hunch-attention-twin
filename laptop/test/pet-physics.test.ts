import { describe, it, expect, beforeEach } from 'vitest'
import { PetPhysics } from '../src/renderer/overlay/pet'

describe('PetPhysics Full-Screen Movement & 2D Dynamics', () => {
  const VW = 1920
  const VH = 1080
  let pet: PetPhysics

  beforeEach(() => {
    pet = new PetPhysics(VW, VH)
  })

  it('can be dragged to all four quadrants of a 1920x1080 viewport without center-clamping', () => {
    const now = 1000

    // Quadrant 1: Top-Left (x < 960, y < 540)
    pet.startDrag(50, 50, now)
    pet.moveDrag(80, 120, now + 16, VW, VH)
    expect(pet.x).toBeLessThan(960)
    expect(pet.y).toBeLessThan(540)
    expect(pet.x).toBeGreaterThanOrEqual(0)
    expect(pet.y).toBeGreaterThanOrEqual(0)
    pet.endDrag(now + 32)

    // Quadrant 2: Top-Right (x >= 960, y < 540)
    pet.startDrag(pet.x + 20, pet.y + 20, now + 50)
    pet.moveDrag(1850, 100, now + 66, VW, VH)
    expect(pet.x).toBeGreaterThanOrEqual(960)
    expect(pet.x).toBeLessThanOrEqual(VW - pet.size)
    expect(pet.y).toBeLessThan(540)
    pet.endDrag(now + 80)

    // Quadrant 3: Bottom-Left (x < 960, y >= 540)
    pet.startDrag(pet.x + 20, pet.y + 20, now + 100)
    pet.moveDrag(60, 1020, now + 116, VW, VH)
    expect(pet.x).toBeLessThan(960)
    expect(pet.y).toBeGreaterThanOrEqual(540)
    expect(pet.y).toBeLessThanOrEqual(VH - pet.size)
    pet.endDrag(now + 130)

    // Quadrant 4: Bottom-Right (x >= 960, y >= 540)
    pet.startDrag(pet.x + 20, pet.y + 20, now + 150)
    pet.moveDrag(1880, 1040, now + 166, VW, VH)
    expect(pet.x).toBeGreaterThanOrEqual(960)
    expect(pet.x).toBeLessThanOrEqual(VW - pet.size)
    expect(pet.y).toBeGreaterThanOrEqual(540)
    expect(pet.y).toBeLessThanOrEqual(VH - pet.size)
    pet.endDrag(now + 180)
  })

  it('clamps drag coordinates strictly within screen bounds [0, VW - size] and [0, VH - size]', () => {
    pet.startDrag(50, 50, 1000)
    // Drag far beyond top-left (-500, -500)
    pet.moveDrag(-500, -500, 1016, VW, VH)
    expect(pet.x).toBe(0)
    expect(pet.y).toBe(0)

    // Drag far beyond bottom-right (5000, 5000)
    pet.moveDrag(5000, 5000, 1032, VW, VH)
    expect(pet.x).toBe(VW - pet.size)
    expect(pet.y).toBe(VH - pet.size)
    pet.endDrag(1048)
  })

  it('calculates fling velocity and wall/floor restitution correctly', () => {
    pet.startDrag(500, 500, 1000)
    pet.moveDrag(550, 520, 1020, VW, VH)
    pet.moveDrag(650, 550, 1040, VW, VH)
    const { flung } = pet.endDrag(1050)
    expect(flung).toBe(true)
    expect(pet.vx).toBeGreaterThan(420)

    // Step physics to simulate bounce against right wall
    pet.x = VW - pet.size - 2
    pet.vx = 500
    pet.isDockedEdge = false
    pet.step(0.016, VW, VH)
    // Wall restitution should invert velocity and scale by 0.65
    expect(pet.vx).toBeLessThan(0)
    expect(pet.x).toBeLessThanOrEqual(VW - pet.size)
  })

  it('detects head rub and shake gestures', () => {
    pet.x = 200
    pet.y = 200

    // Rapid alternating rubs over the pet head
    let rubbed = false
    const baseTime = 1000
    for (let i = 0; i < 6; i++) {
      const offset = (i % 2 === 0 ? -18 : 18)
      if (pet.detectHeadRub(200 + pet.size / 2 + offset, 210, baseTime + i * 40)) {
        rubbed = true
        break
      }
    }
    expect(rubbed).toBe(true)
    expect(pet.emotion).toBe('HAPPY')

    // Shake gesture during drag -> DIZZY
    pet.startDrag(200, 200, 2000)
    let dizzy: string | null = null
    const shakeOffsets = [-30, 30, -30, 30, -30]
    for (let i = 0; i < shakeOffsets.length; i++) {
      dizzy = pet.moveDrag(200 + shakeOffsets[i], 200, 2000 + i * 20, VW, VH)
      if (dizzy === 'DIZZY') break
    }
    expect(dizzy).toBe('DIZZY')
    expect(pet.emotion).toBe('DIZZY')
  })
})

describe('PetPhysics 4-State Dynamic Opacity Engine', () => {
  const VW = 1920
  const VH = 1080
  let pet: PetPhysics

  beforeEach(() => {
    pet = new PetPhysics(VW, VH)
  })

  it('interpolates to GHOST_FLOW target alpha (0.18) during productive work', () => {
    pet.setPresence('GHOST_FLOW', 'FOCUSED', VW, VH)
    expect(pet.targetAlpha).toBe(0.18)

    // Step 60 frames (~1.0s)
    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(0.18, 1)
  })

  it('interpolates to COMPANION_EDGE target alpha (1.0)', () => {
    pet.alpha = 0.18
    pet.setPresence('COMPANION_EDGE', 'CURIOUS', VW, VH)
    expect(pet.targetAlpha).toBe(1.0)

    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(1.0, 1)
  })

  it('walks to screen center during INTERVENTION_WALK_IN and interpolates to 1.0 opacity', () => {
    pet.alpha = 0.18
    pet.x = 1800
    pet.y = 800
    pet.setPresence('INTERVENTION_WALK_IN', 'POUTING', VW, VH)

    expect(pet.targetAlpha).toBe(1.0)
    expect(pet.targetX).toBeCloseTo(VW * 0.5 - pet.size * 0.5, 0)
    expect(pet.targetY).toBeCloseTo(VH * 0.42 - pet.size * 0.5, 0)

    // Step 90 frames (~1.5s)
    for (let i = 0; i < 90; i++) {
      pet.step(1 / 60, VW, VH)
    }

    expect(pet.alpha).toBeCloseTo(1.0, 1)
    expect(pet.x).toBeCloseTo(VW * 0.5 - pet.size * 0.5, 1)
    expect(pet.y).toBeCloseTo(VH * 0.42 - pet.size * 0.5, 1)
  })

  it('interpolates to HIDDEN target alpha (0.0) during DND or pause', () => {
    pet.alpha = 1.0
    pet.setPresence('HIDDEN', 'FOCUSED', VW, VH)
    expect(pet.targetAlpha).toBe(0.0)

    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(0.0, 1)
    expect(pet.hitTest(pet.x + 10, pet.y + 10)).toBe(false)
  })

  it('smoothly overrides desired alpha to 1.0 when hovered or dragged in GHOST_FLOW', () => {
    pet.setPresence('GHOST_FLOW', 'FOCUSED', VW, VH)
    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(0.18, 1)

    // Hover starts
    pet.isHovered = true
    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(1.0, 1)

    // Hover ends
    pet.isHovered = false
    for (let i = 0; i < 60; i++) {
      pet.step(1 / 60, VW, VH)
    }
    expect(pet.alpha).toBeCloseTo(0.18, 1)
  })
})

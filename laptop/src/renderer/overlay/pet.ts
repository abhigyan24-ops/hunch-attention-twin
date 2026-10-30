import type { PetEmotion, PresenceMode } from '../../shared/types'

export class PetPhysics {
  x: number
  y: number
  vx = 0
  vy = 0
  readonly size = 96

  // Jelly squash-and-stretch spring state
  deformation = 0
  deformationVel = 0
  scaleX = 1
  scaleY = 1

  // Interaction & Navigation state
  isDragged = false
  isDockedEdge = true
  isHovered = false
  targetX: number | null = null
  targetY: number | null = null

  // Dynamic opacity & emotion state
  alpha = 0.9
  targetAlpha = 0.9
  ghostAlpha = 0.18
  presenceMode: PresenceMode = 'COMPANION_EDGE'
  emotion: PetEmotion = 'FOCUSED'

  // Pointer history for fling & shake/head-rub detection
  private dragOffsetX = 0
  private dragOffsetY = 0
  private samples: { x: number; y: number; t: number }[] = []
  private rubFlips = 0
  private lastRubDx = 0
  private lastRubTime = 0

  constructor(viewportW: number, viewportH: number) {
    this.x = Math.max(24, viewportW - this.size - 24)
    this.y = Math.max(24, viewportH - this.size - 48)
  }

  impulseSquash(amount: number) {
    this.deformation = Math.max(-0.45, Math.min(0.45, this.deformation + amount))
  }

  setPresence(mode: PresenceMode, emotion: PetEmotion, viewportW: number, viewportH: number) {
    this.presenceMode = mode
    this.emotion = emotion

    if (mode === 'HIDDEN') {
      this.targetAlpha = 0.0
    } else if (mode === 'GHOST_FLOW') {
      this.targetAlpha = this.ghostAlpha
      this.dockToMargin(viewportW, viewportH)
    } else if (mode === 'COMPANION_EDGE') {
      this.targetAlpha = 1.0
      if (!this.isDragged && this.targetX === null) {
        this.dockToMargin(viewportW, viewportH)
      }
    } else if (mode === 'INTERVENTION_WALK_IN') {
      this.targetAlpha = 1.0
      this.walkTo(viewportW * 0.5 - this.size * 0.5, viewportH * 0.42 - this.size * 0.5)
    }
  }

  walkTo(tx: number, ty: number) {
    this.isDockedEdge = false
    this.targetX = tx
    this.targetY = ty
    this.impulseSquash(-0.22)
  }

  dockToMargin(viewportW: number, viewportH: number) {
    const rightX = Math.max(16, viewportW - this.size - 20)
    const leftX = 20
    const destX = this.x + this.size / 2 < viewportW / 2 ? leftX : rightX
    const destY = Math.max(32, Math.min(this.y, viewportH - this.size - 32))
    this.targetX = destX
    this.targetY = destY
  }

  startDrag(px: number, py: number, now: number) {
    this.isDragged = true
    this.isDockedEdge = false
    this.targetX = null
    this.targetY = null
    this.vx = 0
    this.vy = 0
    this.dragOffsetX = Math.max(0, Math.min(this.size, px - this.x))
    this.dragOffsetY = Math.max(0, Math.min(this.size, py - this.y))
    this.samples = [{ x: px, y: py, t: now }]
    this.impulseSquash(0.18)
  }

  moveDrag(px: number, py: number, now: number, viewportW: number, viewportH: number): 'DIZZY' | null {
    if (!this.isDragged) return null
    this.x = Math.max(0, Math.min(viewportW - this.size, px - this.dragOffsetX))
    this.y = Math.max(0, Math.min(viewportH - this.size, py - this.dragOffsetY))

    this.samples.push({ x: px, y: py, t: now })
    while (this.samples.length > 8 && now - this.samples[0].t > 180) {
      this.samples.shift()
    }

    // Detect vigorous shake -> DIZZY
    if (this.samples.length >= 4) {
      let flips = 0
      let prevSign = 0
      for (let i = 1; i < this.samples.length; i++) {
        const dx = this.samples[i].x - this.samples[i - 1].x
        if (Math.abs(dx) > 18) {
          const sign = Math.sign(dx)
          if (prevSign !== 0 && sign !== prevSign) flips++
          prevSign = sign
        }
      }
      if (flips >= 3) {
        this.emotion = 'DIZZY'
        return 'DIZZY'
      }
    }
    return null
  }

  endDrag(now: number): { flung: boolean } {
    if (!this.isDragged) return { flung: false }
    this.isDragged = false

    const first = this.samples[0]
    const last = this.samples[this.samples.length - 1]
    if (first && last && last.t > first.t) {
      const dt = Math.max(0.016, (last.t - first.t) / 1000)
      this.vx = Math.max(-2400, Math.min(2400, (last.x - first.x) / dt))
      this.vy = Math.max(-2400, Math.min(2400, (last.y - first.y) / dt))
    }
    const speed = Math.hypot(this.vx, this.vy)
    return { flung: speed > 420 }
  }

  detectHeadRub(px: number, py: number, now: number): boolean {
    if (this.isDragged || !this.hitTest(px, py)) return false
    if (now - this.lastRubTime > 900) {
      this.rubFlips = 0
    }
    const dx = px - (this.x + this.size / 2)
    if (Math.abs(dx - this.lastRubDx) > 12) {
      if (Math.sign(dx - this.lastRubDx) !== Math.sign(this.lastRubDx)) {
        this.rubFlips++
        this.lastRubTime = now
      }
      this.lastRubDx = dx
    }
    if (this.rubFlips >= 4) {
      this.rubFlips = 0
      this.emotion = 'HAPPY'
      this.impulseSquash(0.25)
      return true
    }
    return false
  }

  step(dt: number, viewportW: number, viewportH: number) {
    if (viewportW <= 0 || viewportH <= 0) return

    // 1. Smooth Dynamic Opacity Interpolation (with Hover Override)
    const desiredAlpha =
      this.presenceMode === 'HIDDEN'
        ? 0.0
        : this.isHovered || this.isDragged
          ? 1.0
          : this.targetAlpha
    this.alpha += (desiredAlpha - this.alpha) * Math.min(1, dt * 8)

    // 2. Hooke's Law Jelly Squash & Stretch
    const springForce = -180 * this.deformation - 14 * this.deformationVel
    this.deformationVel += springForce * dt
    this.deformation += this.deformationVel * dt
    this.scaleX = Math.max(0.65, Math.min(1.45, 1 + this.deformation))
    this.scaleY = 1 / this.scaleX

    if (this.isDragged) return

    // 3. Autonomous Target Steering (Walk-In Intervention or Edge Return)
    if (this.targetX !== null && this.targetY !== null) {
      const dx = this.targetX - this.x
      const dy = this.targetY - this.y
      const dist = Math.hypot(dx, dy)
      if (dist < 6) {
        this.x = this.targetX
        this.y = this.targetY
        this.vx = 0
        this.vy = 0
        this.targetX = null
        this.targetY = null
        this.isDockedEdge = true
        this.impulseSquash(0.24)
      } else {
        const pull = Math.min(1, dt * 6.5)
        this.x += dx * pull
        this.y += dy * pull
      }
      return
    }

    // 4. Free Ballistic Motion, Wall Restitution (0.65) & Floor Restitution (0.55)
    if (!this.isDockedEdge) {
      this.vy += 1500 * dt // Gravity
      this.vx *= Math.pow(0.985, dt * 60)
      this.vy *= Math.pow(0.985, dt * 60)

      this.x += this.vx * dt
      this.y += this.vy * dt

      const maxX = Math.max(0, viewportW - this.size)
      const maxY = Math.max(0, viewportH - this.size - 8)

      if (this.x <= 0) {
        this.x = 0
        this.vx = Math.abs(this.vx) * 0.65
        this.impulseSquash(-0.22)
      } else if (this.x >= maxX) {
        this.x = maxX
        this.vx = -Math.abs(this.vx) * 0.65
        this.impulseSquash(-0.22)
      }

      if (this.y <= 0) {
        this.y = 0
        this.vy = Math.abs(this.vy) * 0.55
      } else if (this.y >= maxY) {
        this.y = maxY
        if (Math.abs(this.vy) > 90) {
          this.vy = -Math.abs(this.vy) * 0.55
          this.impulseSquash(0.26)
        } else {
          this.vy = 0
        }
        this.vx *= 0.88 // Floor sliding friction
      }

      // Edge-docking magnetism when slow near margins
      const speed = Math.hypot(this.vx, this.vy)
      if (speed < 95 && (this.x < 36 || this.x > maxX - 36 || this.y >= maxY - 4)) {
        this.vx = 0
        this.vy = 0
        this.isDockedEdge = true
      }
    }
  }

  hitTest(px: number, py: number): boolean {
    if (this.alpha < 0.05) return false
    return (
      px >= this.x &&
      px <= this.x + this.size &&
      py >= this.y &&
      py <= this.y + this.size
    )
  }
}

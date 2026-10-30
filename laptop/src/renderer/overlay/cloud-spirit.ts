import type { PetEmotion } from '../../shared/types'
import type { PetPhysics } from './pet'

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  alpha: number
  kind: 'SPARKLE' | 'HEART' | 'STEAM' | 'ZZZ'
}

export class CloudSpiritRenderer {
  private particles: Particle[] = []

  spawnParticles(cx: number, cy: number, kind: Particle['kind'], count = 4) {
    for (let i = 0; i < count; i++) {
      this.particles.push({
        x: cx + (Math.random() - 0.5) * 36,
        y: cy - 16 + (Math.random() - 0.5) * 16,
        vx: (Math.random() - 0.5) * 45,
        vy: -35 - Math.random() * 45,
        alpha: 1,
        kind
      })
    }
  }

  draw(
    ctx: CanvasRenderingContext2D,
    pet: PetPhysics,
    cursorX: number,
    cursorY: number,
    nowMs: number
  ) {
    if (pet.alpha <= 0.01) return

    const dt = 0.016
    const cx = pet.x + pet.size / 2
    const cy = pet.y + pet.size / 2 + Math.sin(nowMs * 0.004) * 3.5

    ctx.save()
    ctx.globalAlpha = Math.max(0, Math.min(1, pet.alpha))
    ctx.translate(cx, cy)
    ctx.scale(pet.scaleX, pet.scaleY)

    const palette = getPalette(pet.emotion)

    // 1. Ambient Radial Zen Aura
    const aura = ctx.createRadialGradient(0, 0, 8, 0, 0, 48)
    aura.addColorStop(0, palette.aura)
    aura.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = aura
    ctx.beginPath()
    ctx.arc(0, 0, 48, 0, Math.PI * 2)
    ctx.fill()

    // 2. Plump Multi-Puff 3D Frosted Cloud Silhouette
    const bodyGrad = ctx.createRadialGradient(-10, -14, 4, 0, 6, 36)
    bodyGrad.addColorStop(0, '#FFFFFF')
    bodyGrad.addColorStop(0.55, palette.mid)
    bodyGrad.addColorStop(1, palette.deep)

    ctx.fillStyle = bodyGrad
    ctx.beginPath()
    ctx.arc(-16, 5, 18, 0, Math.PI * 2)
    ctx.arc(16, 5, 18, 0, Math.PI * 2)
    ctx.arc(0, -9, 22, 0, Math.PI * 2)
    ctx.roundRect(-26, -3, 52, 26, 13)
    ctx.fill()

    // Inner frosted glass rim highlight
    ctx.strokeStyle = 'rgba(255,255,255,0.65)'
    ctx.lineWidth = 1.5
    ctx.stroke()

    // 3. Eye-Tracking Offset & Emotion Geometry
    const angle = Math.atan2(cursorY - cy, cursorX - cx)
    const dist = Math.min(4.5, Math.hypot(cursorX - cx, cursorY - cy) * 0.02)
    const lookX = Math.cos(angle) * dist
    const lookY = Math.sin(angle) * dist

    drawFace(ctx, pet.emotion, lookX, lookY, nowMs)
    ctx.restore()

    // 4. Floating Particles (Sparkles, Steam Puffs, Zzz)
    if (pet.emotion === 'ANGRY' && Math.random() < 0.08) {
      this.spawnParticles(cx, cy - 20, 'STEAM', 1)
    } else if (pet.emotion === 'SLEEPY' && Math.random() < 0.04) {
      this.spawnParticles(cx + 12, cy - 18, 'ZZZ', 1)
    } else if ((pet.emotion === 'HAPPY' || pet.emotion === 'PROUD') && Math.random() < 0.05) {
      this.spawnParticles(cx, cy - 16, Math.random() < 0.3 ? 'HEART' : 'SPARKLE', 1)
    }

    ctx.save()
    ctx.globalAlpha = pet.alpha
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i]
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.alpha -= dt * 0.9
      if (p.alpha <= 0) {
        this.particles.splice(i, 1)
        continue
      }
      ctx.globalAlpha = p.alpha * pet.alpha
      ctx.font = '12px system-ui, sans-serif'
      ctx.fillText(
        p.kind === 'STEAM' ? '💢' : p.kind === 'ZZZ' ? 'z' : p.kind === 'HEART' ? '♥' : '✦',
        p.x,
        p.y
      )
    }
    ctx.restore()
  }
}

function getPalette(e: PetEmotion) {
  switch (e) {
    case 'ANGRY':
      return { aura: 'rgba(251,113,133,0.42)', mid: '#FDA4AF', deep: '#E11D48' }
    case 'SAD':
    case 'POUTING':
      return { aura: 'rgba(148,163,184,0.35)', mid: '#CBD5E1', deep: '#64748B' }
    case 'PROUD':
    case 'HAPPY':
      return { aura: 'rgba(124,232,158,0.45)', mid: '#86EFAC', deep: '#10B981' }
    case 'SKEPTICAL':
      return { aura: 'rgba(253,224,71,0.38)', mid: '#FDE047', deep: '#CA8A04' }
    case 'DIZZY':
      return { aura: 'rgba(226,206,241,0.45)', mid: '#D8B4FE', deep: '#9333EA' }
    default:
      return { aura: 'rgba(124,200,248,0.36)', mid: '#93C5FD', deep: '#3B82F6' }
  }
}

function drawFace(
  ctx: CanvasRenderingContext2D,
  emotion: PetEmotion,
  lookX: number,
  lookY: number,
  nowMs: number
) {
  const isBlinking = Math.sin(nowMs * 0.0018) > 0.985

  ;[-10, 10].forEach((ex, idx) => {
    ctx.save()
    ctx.translate(ex + lookX * 0.4, -2 + lookY * 0.4)

    if (emotion === 'SLEEPY' || isBlinking) {
      ctx.strokeStyle = '#121417'
      ctx.lineWidth = 2.2
      ctx.beginPath()
      ctx.arc(0, 2, 5, 0.1 * Math.PI, 0.9 * Math.PI)
      ctx.stroke()
    } else if (emotion === 'HAPPY' || emotion === 'PROUD') {
      ctx.strokeStyle = '#121417'
      ctx.lineWidth = 2.4
      ctx.beginPath()
      ctx.arc(0, 2, 5, 1.1 * Math.PI, 1.9 * Math.PI)
      ctx.stroke()
    } else {
      const eyeH = emotion === 'SKEPTICAL' && idx === 1 ? 3.8 : 7.5
      ctx.fillStyle = '#FFFFFF'
      ctx.beginPath()
      ctx.ellipse(0, 0, 6.8, eyeH, 0, 0, Math.PI * 2)
      ctx.fill()

      const swirl = emotion === 'DIZZY' ? Math.sin(nowMs * 0.02 + idx * 3) * 2.5 : 0
      ctx.fillStyle = '#121417'
      ctx.beginPath()
      ctx.arc(lookX * 0.6 + swirl, lookY * 0.6, 3.8, 0, Math.PI * 2)
      ctx.fill()

      ctx.fillStyle = '#FFFFFF'
      ctx.beginPath()
      ctx.arc(lookX * 0.6 - 1.2, lookY * 0.6 - 1.4, 1.3, 0, Math.PI * 2)
      ctx.fill()

      // Expressive Brows for ANGRY / SAD
      if (emotion === 'ANGRY' || emotion === 'SAD') {
        ctx.strokeStyle = '#121417'
        ctx.lineWidth = 2.2
        ctx.beginPath()
        const slant = emotion === 'ANGRY' ? (idx === 0 ? 3 : -3) : idx === 0 ? -3 : 3
        ctx.moveTo(-5, -7 - slant)
        ctx.lineTo(5, -7 + slant)
        ctx.stroke()
      }
    }
    ctx.restore()
  })

  // Mouth curve
  ctx.strokeStyle = '#121417'
  ctx.lineWidth = 2.1
  ctx.lineCap = 'round'
  ctx.beginPath()
  if (emotion === 'SAD' || emotion === 'POUTING' || emotion === 'ANGRY') {
    ctx.arc(0, 10, 4, 1.15 * Math.PI, 1.85 * Math.PI)
  } else {
    ctx.arc(0, 5, 4.2, 0.15 * Math.PI, 0.85 * Math.PI)
  }
  ctx.stroke()
}

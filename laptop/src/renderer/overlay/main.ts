import type { PetDirective, PetBridgeApi } from '../../shared/types'
import { PetPhysics } from './pet'
import { CloudSpiritRenderer } from './cloud-spirit'
import { NudgeBubble } from './bubble'

declare global {
  interface Window {
    petBridge: PetBridgeApi
  }
}

const canvas = document.getElementById('c') as HTMLCanvasElement
const ctx = canvas.getContext('2d')!

function resize() {
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.floor(window.innerWidth * dpr)
  canvas.height = Math.floor(window.innerHeight * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

window.addEventListener('resize', resize)
resize()

const pet = new PetPhysics(window.innerWidth, window.innerHeight)
const cloudSpirit = new CloudSpiritRenderer()
const bubble = new NudgeBubble()

let cursorX = window.innerWidth / 2
let cursorY = window.innerHeight / 2
let showDebugHud = false
let currentDirective: PetDirective | null = null
let lastIgnore = true

function updateMouseIgnore(now: number) {
  if (pet.isDragged) {
    if (lastIgnore !== false) {
      lastIgnore = false
      window.petBridge?.setIgnoreMouse(false)
    }
    return
  }

  const hoverPet = pet.hitTest(cursorX, cursorY)
  const hoverBubble = bubble.hitTest(cursorX, cursorY, now)
  const shouldInteract = hoverPet || hoverBubble
  pet.isHovered = hoverPet

  if (shouldInteract && lastIgnore !== false) {
    lastIgnore = false
    window.petBridge?.setIgnoreMouse(false)
  } else if (!shouldInteract && lastIgnore !== true) {
    lastIgnore = true
    window.petBridge?.setIgnoreMouse(true)
  }
}

window.addEventListener('pointermove', (e) => {
  cursorX = e.clientX
  cursorY = e.clientY
  const now = performance.now()

  if (pet.isDragged) {
    const reaction = pet.moveDrag(cursorX, cursorY, now, window.innerWidth, window.innerHeight)
    if (reaction === 'DIZZY') {
      cloudSpirit.spawnParticles(pet.x + pet.size / 2, pet.y + pet.size / 2, 'SPARKLE', 5)
    }
  } else {
    if (pet.detectHeadRub(cursorX, cursorY, now)) {
      cloudSpirit.spawnParticles(pet.x + pet.size / 2, pet.y + pet.size / 2, 'HEART', 4)
    }
  }

  updateMouseIgnore(now)
})

window.addEventListener('pointerdown', (e) => {
  cursorX = e.clientX
  cursorY = e.clientY
  const now = performance.now()

  // 1. Check if user clicked an action inside the Nudge Bubble
  const action = bubble.checkActionClick(cursorX, cursorY, now)
  if (action === 'ACCEPT') {
    window.petBridge?.nudgeAccepted()
    bubble.hide()
    pet.emotion = 'HAPPY'
    pet.impulseSquash(0.35)
    cloudSpirit.spawnParticles(pet.x + pet.size / 2, pet.y + pet.size / 2, 'SPARKLE', 8)
    pet.dockToMargin(window.innerWidth, window.innerHeight)
    return
  } else if (action === 'DISMISS') {
    window.petBridge?.nudgeDismissed('button')
    bubble.hide()
    pet.dockToMargin(window.innerWidth, window.innerHeight)
    return
  }

  // 2. Check if user grabbed the pet
  if (pet.hitTest(cursorX, cursorY)) {
    pet.startDrag(cursorX, cursorY, now)
    window.petBridge?.petBooped()
    updateMouseIgnore(now)
  }
})

window.addEventListener('pointerup', () => {
  const now = performance.now()
  if (pet.isDragged) {
    const { flung } = pet.endDrag(now)
    if (flung && bubble.isActive(now)) {
      window.petBridge?.nudgeDismissed('flung')
      bubble.hide()
    }
    updateMouseIgnore(now)
  }
})

window.addEventListener('pointerleave', () => {
  if (!pet.isDragged) {
    pet.isHovered = false
    if (lastIgnore !== true) {
      lastIgnore = true
      window.petBridge?.setIgnoreMouse(true)
    }
  }
})

window.addEventListener('keydown', (e) => {
  if (e.ctrlKey && e.altKey && (e.key === 'd' || e.key === 'D')) {
    e.preventDefault()
    showDebugHud = !showDebugHud
  }
})

if (window.petBridge) {
  window.petBridge.onDirective((directive) => {
    currentDirective = directive
    pet.setPresence(directive.presence, directive.emotion, window.innerWidth, window.innerHeight)
    if (directive.nudgeText) {
      bubble.show(directive.nudgeText, Boolean(directive.isIntervention), performance.now())
    }
  })
}

let lastTime = performance.now()

function render(now: number) {
  const dt = Math.min(0.05, Math.max(0.001, (now - lastTime) / 1000))
  lastTime = now

  pet.step(dt, window.innerWidth, window.innerHeight)

  ctx.clearRect(0, 0, window.innerWidth, window.innerHeight)

  cloudSpirit.draw(ctx, pet, cursorX, cursorY, now)
  bubble.draw(ctx, pet.x + pet.size / 2, pet.y, now)

  if (showDebugHud) {
    drawDebugHud(ctx, pet, currentDirective)
  }

  requestAnimationFrame(render)
}

function drawDebugHud(
  ctx: CanvasRenderingContext2D,
  p: PetPhysics,
  directive: PetDirective | null
) {
  const stats = directive?.telemetry ?? {
    nudgesShown: 0,
    nudgesAccepted: 0,
    nudgesDismissed: 0,
    thresholdMultiplier: 1.0,
    cooldownMs: 90000
  }

  const lines = [
    `PRESENCE: ${p.presenceMode} • ALPHA: ${(p.alpha * 100).toFixed(0)}%`,
    `ALIGNMENT: ${directive?.alignment ?? 'NEUTRAL'} • EMOTION: ${p.emotion}`,
    `TELEMETRY: Shown: ${stats.nudgesShown} | Accepted: ${stats.nudgesAccepted} | Dismissed: ${stats.nudgesDismissed}`,
    `MULTIPLIER: ${stats.thresholdMultiplier.toFixed(2)}x | COOLDOWN: ${(stats.cooldownMs / 1000).toFixed(0)}s`
  ]

  ctx.save()
  ctx.fillStyle = 'rgba(15, 23, 42, 0.88)'
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.roundRect(16, 16, 360, 96, 12)
  ctx.fill()
  ctx.stroke()

  ctx.fillStyle = '#F8FAFC'
  ctx.font = '600 11px monospace'
  lines.forEach((line, i) => {
    ctx.fillText(line, 28, 38 + i * 20)
  })
  ctx.restore()
}

requestAnimationFrame(render)

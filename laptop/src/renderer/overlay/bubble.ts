export class NudgeBubble {
  text = ''
  isIntervention = false
  visibleUntil = 0
  rect = { x: 0, y: 0, w: 0, h: 0 }
  acceptRect = { x: 0, y: 0, w: 0, h: 0 }
  dismissRect = { x: 0, y: 0, w: 0, h: 0 }

  show(text: string, isIntervention: boolean, now: number, durationMs = 9000) {
    this.text = text
    this.isIntervention = isIntervention
    this.visibleUntil = now + durationMs
  }

  hide() {
    this.text = ''
    this.visibleUntil = 0
  }

  isActive(now: number): boolean {
    return Boolean(this.text) && now < this.visibleUntil
  }

  draw(ctx: CanvasRenderingContext2D, anchorX: number, anchorY: number, now: number) {
    if (!this.isActive(now)) return

    const w = 250
    const h = this.isIntervention ? 84 : 48
    const x = Math.max(12, Math.min(anchorX - w / 2, window.innerWidth - w - 12))
    const y = Math.max(12, anchorY - h - 14)
    this.rect = { x, y, w, h }

    ctx.save()
    ctx.fillStyle = 'rgba(255, 255, 255, 0.96)'
    ctx.strokeStyle = 'rgba(18, 20, 23, 0.14)'
    ctx.lineWidth = 1.2
    ctx.beginPath()
    ctx.roundRect(x, y, w, h, 16)
    ctx.fill()
    ctx.stroke()

    ctx.fillStyle = '#121417'
    ctx.font = '500 13px system-ui, sans-serif'
    ctx.fillText(this.text.slice(0, 38), x + 14, y + 26)

    if (this.isIntervention) {
      // [Refocus] pill button
      this.acceptRect = { x: x + 14, y: y + 44, w: 108, h: 28 }
      ctx.fillStyle = '#121417'
      ctx.beginPath()
      ctx.roundRect(this.acceptRect.x, this.acceptRect.y, this.acceptRect.w, this.acceptRect.h, 14)
      ctx.fill()
      ctx.fillStyle = '#FFFFFF'
      ctx.font = '600 12px system-ui, sans-serif'
      ctx.fillText('Refocus', this.acceptRect.x + 28, this.acceptRect.y + 18)

      // [Dismiss ✕] pill button
      this.dismissRect = { x: x + 132, y: y + 44, w: 104, h: 28 }
      ctx.fillStyle = '#F1F5F9'
      ctx.beginPath()
      ctx.roundRect(this.dismissRect.x, this.dismissRect.y, this.dismissRect.w, this.dismissRect.h, 14)
      ctx.fill()
      ctx.fillStyle = '#475569'
      ctx.font = '500 12px system-ui, sans-serif'
      ctx.fillText('Dismiss ✕', this.dismissRect.x + 22, this.dismissRect.y + 18)
    }
    ctx.restore()
  }

  hitTest(px: number, py: number, now: number): boolean {
    if (!this.isActive(now)) return false
    const r = this.rect
    return px >= r.x && px <= r.x + r.w && py >= r.y && py <= r.y + r.h
  }

  checkActionClick(px: number, py: number, now: number): 'ACCEPT' | 'DISMISS' | null {
    if (!this.isActive(now) || !this.isIntervention) return null
    const a = this.acceptRect
    if (px >= a.x && px <= a.x + a.w && py >= a.y && py <= a.y + a.h) return 'ACCEPT'
    const d = this.dismissRect
    if (px >= d.x && px <= d.x + d.w && py >= d.y && py <= d.y + d.h) return 'DISMISS'
    return null
  }
}

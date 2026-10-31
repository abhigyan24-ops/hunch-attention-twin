import { WebSocketServer, WebSocket } from 'ws'
import crypto from 'node:crypto'

interface Room {
  key: Buffer
  peers: Set<WebSocket>
  updatedAt: number
}

const rooms = new Map<string, Room>()
const wss = new WebSocketServer({ port: Number(process.env.PORT || 8080), maxPayload: 1_048_576 })

function safeHmacVerify(key: Buffer, challenge: Buffer, proofBase64: string): boolean {
  const expected = crypto.createHmac('sha256', key).update(challenge).digest()
  let actual: Buffer
  try {
    actual = Buffer.from(proofBase64, 'base64')
  } catch {
    return false
  }
  if (expected.length !== actual.length) return false
  return crypto.timingSafeEqual(expected, actual)
}

wss.on('connection', ws => {
  const challenge = crypto.randomBytes(16)
  let joinedRoom: string | null = null
  ws.send(JSON.stringify({ t: 'challenge', c: challenge.toString('base64') }))

  ws.on('message', (data, isBinary) => {
    if (!joinedRoom) {
      try {
        const m = JSON.parse(data.toString())
        if (typeof m.room !== 'string') return ws.close(4000)
        let r = rooms.get(m.room)
        if (!r && typeof m.newKey === 'string') {
          r = { key: Buffer.from(m.newKey, 'base64'), peers: new Set(), updatedAt: Date.now() }
          rooms.set(m.room, r)
        }
        if (!r || !safeHmacVerify(r.key, challenge, String(m.proof ?? ''))) {
          return ws.close(4003)
        }
        if (r.peers.size >= 2) return ws.close(4009)
        r.peers.add(ws)
        r.updatedAt = Date.now()
        joinedRoom = m.room
        ws.send(JSON.stringify({ t: 'joined', peers: r.peers.size }))
        r.peers.forEach(p => {
          if (p !== ws && p.readyState === WebSocket.OPEN) {
            p.send(JSON.stringify({ t: 'peer-joined' }))
          }
        })
      } catch {
        ws.close(4000)
      }
      return
    }

    const r = rooms.get(joinedRoom)
    if (!r) return ws.close(4001)
    r.updatedAt = Date.now()
    r.peers.forEach(p => {
      if (p !== ws && p.readyState === WebSocket.OPEN) {
        p.send(data, { binary: isBinary })
      }
    })
  })

  ws.on('close', () => {
    if (joinedRoom) {
      const r = rooms.get(joinedRoom)
      r?.peers.delete(ws)
      if (r && r.peers.size === 0) rooms.delete(joinedRoom)
    }
  })
})

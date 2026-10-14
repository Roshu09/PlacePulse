/**
 * A tiny Server-Sent Events client built on fetch(). The browser's own EventSource cannot send an
 * Authorization header, and we do not want the login token in a URL, so we read the stream ourselves.
 */

export interface SseFrame {
  event: string
  data: string
}

/** Splits received text into complete frames. Returns what is left over (an unfinished frame). */
export function parseFrames(buffer: string): { frames: SseFrame[]; rest: string } {
  const frames: SseFrame[] = []
  const text = buffer.replace(/\r\n/g, '\n')
  const parts = text.split('\n\n')
  const rest = parts.pop() ?? ''
  for (const part of parts) {
    let event = 'message'
    const data: string[] = []
    for (const line of part.split('\n')) {
      if (line === '' || line.startsWith(':')) continue // blank or a comment
      const colon = line.indexOf(':')
      const field = colon === -1 ? line : line.slice(0, colon)
      let value = colon === -1 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') event = value
      else if (field === 'data') data.push(value)
    }
    if (data.length > 0 || event !== 'message') frames.push({ event, data: data.join('\n') })
  }
  return { frames, rest }
}

export interface StreamHandlers {
  /** The server accepted the connection. `reconnect` is true for every open after the first. */
  onOpen: (reconnect: boolean) => void
  onClose: () => void
  onEvent: (event: string, data: string) => void
  /** The token was rejected: reconnecting would never work. */
  onUnauthorized: () => void
}

const SILENCE_LIMIT_MS = 60_000 // the server pings every 20s, so a minute of silence means a dead connection
const MIN_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 15_000

/** Keeps a stream open, reconnecting with a growing pause. Returns a function that stops it. */
export function openStream(url: string, getToken: () => string, handlers: StreamHandlers): () => void {
  let stopped = false
  let controller: AbortController | null = null
  let everOpened = false

  async function loop() {
    let backoff = MIN_BACKOFF_MS
    while (!stopped) {
      controller = new AbortController()
      const ctrl = controller
      let silence: ReturnType<typeof setTimeout> | undefined
      const arm = () => {
        clearTimeout(silence)
        silence = setTimeout(() => ctrl.abort(), SILENCE_LIMIT_MS)
      }
      try {
        arm()
        const res = await fetch(url, {
          headers: { Authorization: `Bearer ${getToken()}`, Accept: 'text/event-stream' },
          signal: ctrl.signal,
        })
        if (res.status === 401) {
          handlers.onUnauthorized()
          return
        }
        if (!res.ok || !res.body) throw new Error(`stream refused: ${res.status}`)

        handlers.onOpen(everOpened)
        everOpened = true
        backoff = MIN_BACKOFF_MS

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          arm()
          buffer += decoder.decode(value, { stream: true })
          const { frames, rest } = parseFrames(buffer)
          buffer = rest
          for (const f of frames) handlers.onEvent(f.event, f.data)
        }
      } catch {
        // network error, timeout or abort: fall through to the pause and try again
      } finally {
        clearTimeout(silence)
      }
      if (stopped) return
      handlers.onClose()
      await new Promise((r) => setTimeout(r, backoff))
      backoff = Math.min(backoff * 2, MAX_BACKOFF_MS)
    }
  }

  void loop()

  return () => {
    stopped = true
    controller?.abort()
  }
}

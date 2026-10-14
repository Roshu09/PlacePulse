import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from './api'
import { useLive, type Topic } from './realtime'

interface State<T> {
  data: T | null
  error: string | null
  loading: boolean
}

/**
 * Runs `fn` on mount and whenever `reload` is called. `fn` must be stable (wrap in useCallback).
 * Pass a `topic` and the data also reloads, quietly and without a loading flash, whenever a live
 * event says that kind of data changed. Only the newest request may update the screen.
 */
export function useLoad<T>(fn: () => Promise<T>, topic?: Topic) {
  const [state, setState] = useState<State<T>>({ data: null, error: null, loading: true })
  const live = useLive(topic)
  const latest = useRef(0)

  const run = useCallback(() => {
    const id = ++latest.current
    fn()
      .then((data) => id === latest.current && setState({ data, error: null, loading: false }))
      .catch((e: unknown) => {
        if (id !== latest.current) return
        setState((s) => ({ data: s.data, error: e instanceof ApiError ? e.message : 'Something went wrong. Try again.', loading: false }))
      })
  }, [fn])

  useEffect(() => {
    run()
    return () => {
      latest.current += 1 // ignore a response that arrives after unmount or after a newer request started
    }
  }, [run, live])

  const reload = useCallback(() => run(), [run])

  const setData = useCallback((updater: (prev: T) => T) => {
    setState((s) => (s.data ? { ...s, data: updater(s.data) } : s))
  }, [])

  return { ...state, reload, setData }
}

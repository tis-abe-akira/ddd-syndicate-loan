import { useSyncExternalStore } from 'react'

/**
 * The acting user for the development backend (X-Acting-User), kept in this browser only.
 * Replaced by real login in UC-006.
 */
const KEY = 'syndicate-loan.acting-user'
const listeners = new Set<() => void>()

export function actingUserId(): number | undefined {
  try {
    const value = Number(localStorage.getItem(KEY))
    return Number.isInteger(value) && value > 0 ? value : undefined
  } catch {
    return undefined
  }
}

export function setActingUserId(userId: number | undefined) {
  try {
    if (userId === undefined) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, String(userId))
  } catch {
    // Storage blocked: the choice lasts until reload only through the listeners below.
  }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useActingUserId() {
  return useSyncExternalStore(subscribe, actingUserId, () => undefined)
}

// App state, and how server events change it.
import { useEffect, useState } from '../lib/preact-htm.js'

const initial = {
  status: 'loading', // loading | joining | connecting | ready | reconnecting | ended | removed
  serverName: 'Campfire',
  me: null,
  token: null,
  members: {}, // id -> MemberInfo (people here now)
  known: {}, // id -> MemberInfo (anyone ever seen, for names on old messages)
  rooms: [],
  convs: {}, // conv -> { messages, loaded, receipts: {memberId: upTo}, unread }
  active: null,
  typing: {}, // conv -> { memberId: expiresAt }
  pending: {}, // clientId -> true while a send is in flight
  uploads: [], // { id, conv, name, progress, xhr }
  replyTo: null,
  editing: null,
  dialog: null,
  menu: null,
  lightbox: null,
  toasts: [],
  showMembers: false,
  joinError: '',
  theme: load('campfire.theme') || 'system',
  sound: load('campfire.sound') !== 'off',
  limit: {}, // conv -> how many recent messages to render
}

let state = initial
const listeners = new Set()
let scheduled = false

export const getState = () => state

/** Merges a patch (object or function of state) and re-renders once per frame. */
export function setState(patch) {
  const p = typeof patch === 'function' ? patch(state) : patch
  if (!p) return
  state = { ...state, ...p }
  if (!scheduled) {
    scheduled = true
    queueMicrotask(() => { scheduled = false; listeners.forEach((l) => l()) })
  }
}

/** Calls [l] after every state change; returns a function that stops it. */
export function subscribe(l) { listeners.add(l); return () => listeners.delete(l) }

export function useStore() {
  const [, force] = useState(0)
  const seen = state
  useEffect(() => {
    const l = () => force((n) => n + 1)
    listeners.add(l)
    // State may have changed between render and subscribing.
    if (state !== seen) l()
    return () => listeners.delete(l)
  }, [])
  return state
}

export function load(key) { try { return localStorage.getItem(key) } catch (e) { return null } }
export function save(key, value) { try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value) } catch (e) {} }

// Lookups

export const PUBLIC = 'room:public'
export const roomOf = (s, conv) => (conv?.startsWith('room:') ? s.rooms.find((r) => 'room:' + r.id === conv) : null)
export const person = (s, id) => s.members[id] || s.known[id] || { id, name: 'Someone', color: 9, online: false }
export function dmOther(s, conv) {
  const [, a, b] = conv.split(':')
  return a === s.me?.id ? b : a
}
export function convTitle(s, conv) {
  if (!conv) return ''
  const room = roomOf(s, conv)
  if (room) return room.name
  if (conv.startsWith('dm:')) return person(s, dmOther(s, conv)).name
  return ''
}
export const isAdmin = (s, room) => !!room && room.id !== 'public' && (s.me?.isHost || room.creatorId === s.me?.id)

export function toast(text) {
  const id = Math.random().toString(36).slice(2)
  setState((s) => ({ toasts: [...s.toasts, { id, text }] }))
  setTimeout(() => setState((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 3500)
}

function withConv(s, conv, change) {
  const c = s.convs[conv] || { messages: [], loaded: false, receipts: {}, unread: 0 }
  return { convs: { ...s.convs, [conv]: { ...c, ...change(c) } } }
}

function dropConv(s, conv) {
  const convs = { ...s.convs }
  delete convs[conv]
  return { convs, active: s.active === conv ? PUBLIC : s.active }
}

// Server events

export function applyEvent(ev, hooks) {
  switch (ev.type) {
    case 'welcome': {
      save('campfire.token', ev.token)
      setState((s) => {
        const convs = {}
        ev.convs.forEach((c) => {
          const old = s.convs[c.conv]
          convs[c.conv] = { messages: old?.messages || (c.last ? [c.last] : []), loaded: false, receipts: old?.receipts || {}, unread: c.unread }
        })
        const members = Object.fromEntries(ev.members.map((m) => [m.id, m]))
        // First join opens Public; a reconnect keeps you where you were, chat list included.
        const active = s.active && convs[s.active] ? s.active : (s.me ? null : PUBLIC)
        return { status: 'ready', me: ev.you, token: ev.token, serverName: ev.serverName, rooms: ev.rooms, members, known: { ...s.known, ...members }, convs, active, joinError: '' }
      })
      hooks.afterWelcome()
      break
    }
    case 'rooms':
      setState({ rooms: ev.rooms })
      break
    case 'members':
      setState((s) => {
        const members = Object.fromEntries(ev.members.map((m) => [m.id, m]))
        const me = members[s.me?.id] || s.me
        return { members, known: { ...s.known, ...members }, me }
      })
      break
    case 'history': {
      const receipts = Object.fromEntries(ev.receipts.map((r) => [r.memberId, r.upTo]))
      setState((s) => withConv(s, ev.conv, () => ({ messages: ev.messages, loaded: true, receipts })))
      hooks.afterHistory(ev.conv)
      break
    }
    case 'message': {
      const m = ev.message
      setState((s) => {
        const pending = { ...s.pending }
        if (ev.clientId) delete pending[ev.clientId]
        const visible = s.active === m.conv && document.visibilityState === 'visible'
        const counts = m.from !== s.me?.id && !visible
        const typing = { ...s.typing, [m.conv]: { ...(s.typing[m.conv] || {}) } }
        delete typing[m.conv][m.from]
        return {
          pending,
          typing,
          ...withConv(s, m.conv, (c) => ({
            messages: c.messages.some((x) => x.id === m.id) ? c.messages : [...c.messages, m],
            unread: counts ? c.unread + 1 : c.unread,
            receipts: m.from === s.me?.id ? { ...c.receipts, [m.from]: m.id } : c.receipts,
          })),
        }
      })
      hooks.afterMessage(m)
      break
    }
    case 'message_updated': {
      const m = ev.message
      setState((s) => withConv(s, m.conv, (c) => ({ messages: c.messages.map((x) => (x.id === m.id ? m : x)) })))
      break
    }
    case 'receipt':
      setState((s) => withConv(s, ev.conv, (c) => ({ receipts: { ...c.receipts, [ev.memberId]: ev.upTo } })))
      break
    case 'typing':
      setState((s) => ({ typing: { ...s.typing, [ev.conv]: { ...(s.typing[ev.conv] || {}), [ev.memberId]: Date.now() + 3500 } } }))
      break
    case 'opened':
      setState((s) => ({ ...withConv(s, ev.conv, () => ({ unread: 0 })), active: ev.conv, dialog: null }))
      break
    case 'room_closed':
      setState((s) => dropConv(s, 'room:' + ev.roomId))
      toast(`${ev.name} was closed`)
      break
    case 'removed':
      if (ev.roomId == null) {
        save('campfire.token', null)
        setState({ status: 'removed' })
      } else {
        const name = getState().rooms.find((r) => r.id === ev.roomId)?.name || 'the room'
        setState((s) => dropConv(s, 'room:' + ev.roomId))
        toast(`You were removed from ${name}`)
      }
      break
    case 'error':
      hooks.onError(ev)
      break
    case 'ended':
      save('campfire.token', null)
      setState({ status: 'ended' })
      break
  }
}

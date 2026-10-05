// The connection to the host phone: hello, reconnects, queued commands, uploads.
import { applyEvent, getState, load, PUBLIC, save, setState, toast } from './store.js'
import { playPop, refreshTitle } from './attention.js'

let ws = null
let attempt = 0
let queue = []
let joinRequest = null
let lastRead = {}

const hostToken = (() => {
  const m = location.hash.match(/host=([a-z0-9]+)/i)
  if (m) {
    try { sessionStorage.setItem('campfire.host', m[1]) } catch (e) {}
    history.replaceState(null, '', location.pathname)
    return m[1]
  }
  try { return sessionStorage.getItem('campfire.host') } catch (e) { return null }
})()
export const isHostDevice = !!hostToken

export async function boot() {
  try {
    const info = await (await fetch('/api/info')).json()
    setState({ serverName: info.name })
    document.title = info.name
  } catch (e) {}
  if (load('campfire.token')) {
    setState({ status: 'connecting' })
    connect()
  } else {
    setState({ status: 'joining' })
  }
}

/** Called from the join screen. */
export function join(name, color) {
  joinRequest = { name, color }
  setState({ joinError: '' })
  if (ws && ws.readyState === WebSocket.OPEN) hello()
  else connect()
}

function connect() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws`)
  ws.onopen = () => { attempt = 0; hello() }
  ws.onmessage = (e) => {
    let ev
    try { ev = JSON.parse(e.data) } catch (err) { return }
    applyEvent(ev, hooks)
  }
  ws.onclose = () => {
    const { status } = getState()
    if (status === 'ended' || status === 'removed' || status === 'joining') return
    setState({ status: 'reconnecting' })
    const wait = Math.min(8000, 500 * 2 ** attempt++)
    setTimeout(connect, wait)
  }
}

function hello() {
  const token = load('campfire.token')
  if (token) raw({ type: 'hello', token })
  else if (joinRequest) raw({ type: 'hello', name: joinRequest.name, color: joinRequest.color, hostToken })
}

function raw(cmd) {
  if (ws && ws.readyState === WebSocket.OPEN) { ws.send(JSON.stringify(cmd)); return true }
  return false
}

/** Sends now, or after reconnecting. Typing signals are never queued. */
export function send(cmd) {
  if (getState().status === 'ready' && raw(cmd)) return
  if (cmd.type !== 'typing' && cmd.type !== 'read') queue.push(cmd)
}

const hooks = {
  afterWelcome() {
    const s = getState()
    if (s.active) raw({ type: 'history', conv: s.active })
    const pending = queue
    queue = []
    pending.forEach(raw)
    refreshTitle()
  },
  afterHistory(conv) {
    if (conv === getState().active) markRead()
    refreshTitle()
  },
  afterMessage(m) {
    const s = getState()
    if (m.from !== s.me?.id && (m.conv !== s.active || document.visibilityState !== 'visible') && s.sound) playPop()
    if (m.conv === s.active) markRead()
    refreshTitle()
  },
  onError(ev) {
    if (ev.code === 'session_ended') {
      save('campfire.token', null)
      setState({ status: 'joining', joinError: joinRequest ? '' : ev.message })
      if (joinRequest) hello()
      return
    }
    if (ev.code === 'bad_name' && getState().status !== 'ready') {
      setState({ status: 'joining', joinError: ev.message })
      return
    }
    if (ev.code === 'banned') {
      setState({ status: 'removed' })
      return
    }
    if (ev.code === 'wrong_password') {
      setState((s) => (s.dialog?.kind === 'password' ? { dialog: { ...s.dialog, error: ev.message } } : null))
      return
    }
    toast(ev.message)
  },
}

/** Tells the server we've seen the open conversation, when the page is actually visible. */
export function markRead() {
  const s = getState()
  const c = s.convs[s.active]
  if (!c || document.visibilityState !== 'visible') return
  const last = c.messages[c.messages.length - 1]
  if (!last) return
  if (c.unread) setState((st) => ({ convs: { ...st.convs, [st.active]: { ...st.convs[st.active], unread: 0 } } }))
  if ((c.receipts[s.me?.id] || 0) >= last.id || lastRead[s.active] >= last.id) return
  lastRead[s.active] = last.id
  send({ type: 'read', conv: s.active, upTo: last.id })
  refreshTitle()
}
const seenNow = () => { markRead(); refreshTitle() }
document.addEventListener('visibilitychange', seenNow)
addEventListener('focus', seenNow)
addEventListener('pointerdown', seenNow, { passive: true })

export function openConv(conv) {
  setState((s) => ({ active: conv, replyTo: null, editing: null, showMembers: s.showMembers && conv.startsWith('room:'), convs: { ...s.convs, [conv]: { ...(s.convs[conv] || { messages: [], receipts: {} }), unread: 0 } } }))
  send({ type: 'history', conv })
  refreshTitle()
}

export function leaveCampfire() {
  save('campfire.token', null)
  location.reload()
}

// Uploads go over HTTP so big files don't block the chat socket.

export function upload(file, conv, text) {
  const s = getState()
  const id = Math.random().toString(36).slice(2)
  const xhr = new XMLHttpRequest()
  const params = new URLSearchParams({ token: s.token, name: file.name || 'file', mime: file.type || 'application/octet-stream' })
  xhr.open('PUT', `/api/upload?${params}`)
  const update = (patch) => setState((st) => ({ uploads: st.uploads.map((u) => (u.id === id ? { ...u, ...patch } : u)) }))
  const remove = () => setState((st) => ({ uploads: st.uploads.filter((u) => u.id !== id) }))
  xhr.upload.onprogress = (e) => e.lengthComputable && update({ progress: e.loaded / e.total })
  xhr.onload = () => {
    remove()
    if (xhr.status !== 200) { toast(xhr.responseText || 'The file could not be sent.'); return }
    const attachment = JSON.parse(xhr.responseText)
    send({ type: 'send', conv, text: text || '', attachment, clientId: id })
  }
  xhr.onerror = () => { remove(); toast(`${file.name} could not be sent.`) }
  xhr.onabort = remove
  setState((st) => ({ uploads: [...st.uploads, { id, conv, name: file.name, progress: 0, xhr }] }))
  xhr.send(file)
}

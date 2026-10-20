import { html, useEffect, useRef, useState } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { QUICK } from './message.js'
import { fileUrl } from '../format.js'
import { getState, save, setState, toast, useStore } from '../store.js'
import { leaveCampfire, send } from '../socket.js'

const EMOJI = [...QUICK, '👏', '🙏', '🎉', '💯', '✅', '👀', '🤔', '😍', '🥲', '😎', '🙌', '💪', '🤝', '👋', '🫶', '🤣',
  '😅', '😬', '😴', '🤯', '🥳', '😇', '🙃', '😐', '😡', '🤗', '🍕', '☕', '🌲', '⭐', '🌙', '☀️', '⚡', '🎶', '📌', '❓', '➕', '👎']

const close = () => setState({ dialog: null })

function Shell({ title, body, children, onSubmit }) {
  const ref = useRef()
  useEffect(() => {
    ref.current?.querySelector('input, button.btn')?.focus()
    const key = (e) => e.key === 'Escape' && close()
    addEventListener('keydown', key)
    return () => removeEventListener('keydown', key)
  }, [])
  return html`<div class="scrim" onClick=${(e) => e.target === e.currentTarget && close()}>
    <form class="dialog" ref=${ref} role="dialog" aria-modal="true" aria-label=${title}
      onSubmit=${(e) => { e.preventDefault(); onSubmit?.() }}>
      <h3>${title}</h3>${body && html`<p>${body}</p>`}${children}
    </form>
  </div>`
}

function NewPoll({ dialog }) {
  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState(['', ''])
  const ready = question.trim() && options.filter((o) => o.trim()).length >= 2
  return html`<${Shell} title="New poll" onSubmit=${() => {
    if (!ready) return
    send({ type: 'send', conv: dialog.conv, poll: { question: question.trim(), options: options.map((o) => o.trim()).filter(Boolean) } })
    close()
  }}>
    <div class="stackf">
      <div><label class="label" for="pq">Question</label><input id="pq" class="field" maxlength="300" value=${question} onInput=${(e) => setQuestion(e.target.value)} placeholder="Where shall we eat?" /></div>
      ${options.map((o, i) => html`<div><label class="label" for=${'po' + i}>Choice ${i + 1}</label><input id=${'po' + i} class="field" maxlength="100" value=${o}
        onInput=${(e) => setOptions(options.map((x, j) => (j === i ? e.target.value : x)))} /></div>`)}
      ${options.length < 10 && html`<button type="button" class="btn ghost" onClick=${() => setOptions([...options, ''])}>Add a choice</button>`}
    </div>
    <div class="actions"><button type="button" class="btn ghost" onClick=${close}>Cancel</button><button class="btn" disabled=${!ready}>Send poll</button></div>
  </${Shell}>`
}

function CreateRoom() {
  const [name, setName] = useState('')
  const [pw, setPw] = useState('')
  return html`<${Shell} title="Create a room" body="Anyone on this Campfire can see the room in the list." onSubmit=${() => {
    if (!name.trim()) return
    send({ type: 'create_room', name, password: pw || null })
  }}>
    <div class="stackf">
      <div><label class="label" for="rn">Name</label><input id="rn" class="field" maxlength="40" value=${name} onInput=${(e) => setName(e.target.value)} placeholder="Snacks, Study group, Game night…" /></div>
      <div><label class="label" for="rp">Password (optional)</label><input id="rp" class="field" type="password" value=${pw} onInput=${(e) => setPw(e.target.value)} placeholder="Leave empty for an open room" autocomplete="new-password" /></div>
    </div>
    <div class="actions"><button type="button" class="btn ghost" onClick=${close}>Cancel</button><button class="btn" disabled=${!name.trim()}>Create room</button></div>
  <//>`
}

function Password({ dialog }) {
  const s = getState()
  const room = s.rooms.find((r) => r.id === dialog.roomId)
  const [pw, setPw] = useState('')
  return html`<${Shell} title=${`Join ${room?.name || 'room'}`} body="This room has a password. Ask whoever made it." onSubmit=${() => send({ type: 'join_room', roomId: dialog.roomId, password: pw })}>
    <input class="field" type="password" value=${pw} onInput=${(e) => setPw(e.target.value)} aria-label="Room password" autocomplete="off" />
    <div class="error-text" role="alert">${dialog.error || ''}</div>
    <div class="actions"><button type="button" class="btn ghost" onClick=${close}>Cancel</button><button class="btn" disabled=${!pw}>Join</button></div>
  <//>`
}

function RoomSettings({ dialog }) {
  const room = getState().rooms.find((r) => r.id === dialog.roomId)
  const [name, setName] = useState(room?.name || '')
  const [pw, setPw] = useState('')
  if (!room) return null
  return html`<${Shell} title="Room settings" onSubmit=${() => {
    send({ type: 'update_room', roomId: room.id, name, password: pw || null })
    close()
  }}>
    <div class="stackf">
      <div><label class="label" for="sn">Name</label><input id="sn" class="field" maxlength="40" value=${name} onInput=${(e) => setName(e.target.value)} /></div>
      <div><label class="label" for="sp">${room.locked ? 'New password' : 'Password'}</label>
        <input id="sp" class="field" type="password" value=${pw} onInput=${(e) => setPw(e.target.value)} placeholder=${room.locked ? 'Leave empty to keep the current one' : 'Leave empty for an open room'} autocomplete="new-password" />
        ${room.locked && html`<button type="button" class="btn danger" style="padding:0 2px;height:36px" onClick=${() => { send({ type: 'update_room', roomId: room.id, clearPassword: true }); close() }}>Remove password</button>`}
      </div>
    </div>
    <div class="actions"><button type="button" class="btn ghost" onClick=${close}>Cancel</button><button class="btn" disabled=${!name.trim()}>Save</button></div>
  <//>`
}

function Confirm({ dialog }) {
  return html`<${Shell} title=${dialog.title} body=${dialog.body} onSubmit=${() => { send(dialog.cmd); close() }}>
    <div class="actions"><button type="button" class="btn ghost" onClick=${close}>Cancel</button><button class="btn" style="background:var(--danger)">${dialog.action}</button></div>
  <//>`
}

function Privacy() {
  return html`<${Shell} title="How private is Campfire?">
    <div class="hint" style="font-size:14px;line-height:1.55">
      <p style="margin:0 0 10px">Campfire runs on the host's phone and never touches the internet. When the host ends it, every message, room and file is deleted.</p>
      <p style="margin:0 0 10px">It is not encrypted. Someone watching this Wi-Fi network could read messages and room passwords, and the host's phone relays direct messages, so the host could technically read them. Don't share anything secret here.</p>
      <p style="margin:0">Link previews are fetched by the host's phone, so the sites you link see the host's internet connection, not yours.</p>
    </div>
    <div class="actions"><button type="button" class="btn" onClick=${close}>Got it</button></div>
  <//>`
}

export function Dialogs() {
  const s = useStore()
  const d = s.dialog
  if (!d) return null
  switch (d.kind) {
    case 'createRoom': return html`<${CreateRoom} />`
    case 'password': return html`<${Password} dialog=${d} />`
    case 'roomSettings': return html`<${RoomSettings} dialog=${d} />`
    case 'confirm': return html`<${Confirm} dialog=${d} />`
    case 'privacy': return html`<${Privacy} />`
    case 'poll': return html`<${NewPoll} dialog=${d} />`
    default: return null
  }
}

function place(rect, width = 240) {
  const left = Math.max(8, Math.min(rect.right - width, innerWidth - width - 8))
  const below = rect.bottom + 6
  return below + 320 > innerHeight ? { left, bottom: innerHeight - rect.top + 6 } : { left, top: below }
}

export function Menus() {
  const s = useStore()
  const m = s.menu
  useEffect(() => {
    if (!m) return
    const key = (e) => e.key === 'Escape' && setState({ menu: null })
    addEventListener('keydown', key)
    return () => removeEventListener('keydown', key)
  }, [m])
  if (!m) return null
  const done = (fn) => () => { setState({ menu: null }); fn() }
  const pos = place(m.x, m.kind === 'emoji' ? 330 : 240)
  let body
  if (m.kind === 'main') {
    const setTheme = (t) => {
      save('campfire.theme', t === 'system' ? null : t)
      if (t === 'system') delete document.documentElement.dataset.theme
      else document.documentElement.dataset.theme = t
      setState({ theme: t })
    }
    body = html`
      <div class="seg" role="group" aria-label="Theme">${['system', 'light', 'dark'].map((t) => html`<button aria-pressed=${s.theme === t} onClick=${() => setTheme(t)}>${t[0].toUpperCase() + t.slice(1)}</button>`)}</div>
      <button onClick=${() => { const on = !s.sound; save('campfire.sound', on ? null : 'off'); setState({ sound: on }) }}>${Icon.bell()} Sound for new messages: ${s.sound ? 'on' : 'off'}</button>
      <button onClick=${done(() => setState({ dialog: { kind: 'privacy' } }))}>${Icon.shield()} How private is Campfire?</button>
      <hr />
      <button onClick=${done(leaveCampfire)}>${Icon.door()} Leave and forget me</button>`
  } else if (m.kind === 'room') {
    body = html`
      ${m.admin && html`<button onClick=${done(() => setState({ dialog: { kind: 'roomSettings', roomId: m.roomId } }))}>${Icon.gear()} Room settings</button>`}
      <button onClick=${done(() => { send({ type: 'leave_room', roomId: m.roomId }); setState((st) => ({ active: 'room:public' })) })}>${Icon.door()} Leave room</button>
      ${m.admin && html`<hr /><button style="color:var(--danger)" onClick=${done(() => setState({ dialog: { kind: 'confirm', title: 'Close this room?', body: 'Everyone is moved out and its messages are deleted.', action: 'Close room', cmd: { type: 'close_room', roomId: m.roomId } } }))}>${Icon.trash()} Close room</button>`}`
  } else if (m.kind === 'message') {
    const msg = m.msg
    body = html`
      <button onClick=${done(() => { setState({ replyTo: msg, editing: null }); document.getElementById('composer')?.focus() })}>${Icon.reply()} Reply</button>
      ${msg.text && html`<button onClick=${done(() => navigator.clipboard?.writeText(msg.text).then(() => toast('Copied'), () => toast("Couldn't copy here")))}>${Icon.copy()} Copy text</button>`}
      ${m.mine && msg.text && !msg.poll && html`<button onClick=${done(() => setState({ editing: msg, replyTo: null }))}>${Icon.edit()} Edit</button>`}
      ${m.canPin && html`<button onClick=${done(() => send({ type: 'pin', msgId: msg.id, pinned: !msg.pinned }))}>${Icon.pin(18)} ${msg.pinned ? 'Unpin' : 'Pin to the top'}</button>`}
      ${msg.attachment && html`<a class="sr" href=${fileUrl(msg.attachment)}></a>`}
      ${m.canDelete && html`<hr /><button style="color:var(--danger)" onClick=${done(() => send({ type: 'delete', msgId: msg.id }))}>${Icon.trash()} Delete</button>`}`
  } else if (m.kind === 'emoji') {
    body = html`<div class="picker">${EMOJI.map((e) => html`<button aria-label=${e} onClick=${done(() => send({ type: 'react', msgId: m.msgId, emoji: e }))}>${e}</button>`)}</div>`
  }
  return html`<div class="scrim" style="background:transparent;display:block" onClick=${() => setState({ menu: null })}>
    <div class="menu" role="menu" style=${pos} onClick=${(e) => e.stopPropagation()}>${body}</div>
  </div>`
}

export function Toasts() {
  const s = useStore()
  return html`<div class="toasts" role="status" aria-live="polite">${s.toasts.map((t) => html`<div class="toast" key=${t.id}>${t.text}</div>`)}</div>`
}

export function Lightbox() {
  const s = useStore()
  const att = s.lightbox
  useEffect(() => {
    if (!att) return
    const key = (e) => e.key === 'Escape' && setState({ lightbox: null })
    addEventListener('keydown', key)
    return () => removeEventListener('keydown', key)
  }, [att])
  if (!att) return null
  return html`<div class="lightbox" role="dialog" aria-label=${att.name} onClick=${() => setState({ lightbox: null })}>
    <img src=${fileUrl(att)} alt=${att.name} />
    <a href=${fileUrl(att)} download=${att.name} onClick=${(e) => e.stopPropagation()}>Download</a>
  </div>`
}

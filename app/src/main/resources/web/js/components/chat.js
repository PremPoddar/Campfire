import { html, useEffect, useLayoutEffect, useRef, useState } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { Message } from './message.js'
import { Composer } from './composer.js'
import { dayLabel, sameDay } from '../format.js'
import { convTitle, dmOther, isAdmin, person, roomOf, setState, useStore } from '../store.js'
import { markRead, upload } from '../socket.js'

const GROUP_GAP = 5 * 60 * 1000
const PAGE = 150

function seenLine(s, conv, msg, c) {
  const others = Object.entries(c.receipts).filter(([id, upTo]) => id !== s.me.id && upTo >= msg.id).map(([id]) => person(s, id).name)
  if (conv.startsWith('dm:')) return { text: others.length ? 'Seen' : 'Sent', title: '' }
  if (!others.length) return null
  return { text: `Seen by ${others.length}`, title: others.join(', ') }
}

export function Chat() {
  const s = useStore()
  const conv = s.active
  const c = s.convs[conv] || { messages: [], receipts: {}, loaded: false }
  const room = roomOf(s, conv)
  const list = useRef()
  const nearBottom = useRef(true)
  const [selected, setSelected] = useState(null)
  const [newBelow, setNewBelow] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [, tick] = useState(0)

  // Typing indicators expire on their own; refresh while any are showing.
  const typingNow = Object.values(s.typing[conv] || {}).some((t) => t > Date.now())
  useEffect(() => {
    if (!typingNow) return
    const t = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [typingNow, conv])

  useLayoutEffect(() => {
    nearBottom.current = true
    setNewBelow(false)
    setSelected(null)
    const el = list.current
    if (el) el.scrollTop = el.scrollHeight
  }, [conv, c.loaded])

  const last = c.messages[c.messages.length - 1]
  useLayoutEffect(() => {
    const el = list.current
    if (!el || !last) return
    if (nearBottom.current || last.from === s.me.id) {
      el.scrollTop = el.scrollHeight
      setNewBelow(false)
    } else {
      setNewBelow(true)
    }
  }, [last?.id])

  // Images and videos load after layout; keep the reader pinned to the bottom if they were there.
  useEffect(() => {
    const el = list.current
    if (!el) return
    const onLoad = () => { if (nearBottom.current) el.scrollTop = el.scrollHeight }
    el.addEventListener('load', onLoad, true)
    el.addEventListener('loadedmetadata', onLoad, true)
    return () => { el.removeEventListener('load', onLoad, true); el.removeEventListener('loadedmetadata', onLoad, true) }
  }, [conv])

  const onScroll = () => {
    const el = list.current
    nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    if (nearBottom.current) { setNewBelow(false); markRead() }
  }

  if (!conv) return html`<section class="chat"><div class="empty"><div><b>Pick a conversation</b>Choose a room or a person on the left.</div></div></section>`

  const limit = s.limit[conv] || PAGE
  const shown = c.messages.slice(-limit)
  const byId = new Map(c.messages.map((m) => [m.id, m]))
  const lastMine = [...c.messages].reverse().find((m) => m.from === s.me.id && !m.deleted)
  const isDm = conv.startsWith('dm:')
  const other = isDm ? person(s, dmOther(s, conv)) : null
  const online = room ? room.members.filter((id) => s.members[id]?.online).length : 0
  const typers = Object.entries(s.typing[conv] || {}).filter(([id, t]) => t > Date.now() && id !== s.me.id)

  const subtitle = typers.length
    ? html`<small class="typing">${typers.length === 1 ? `${person(s, typers[0][0]).name} is typing…` : 'Several people are typing…'}</small>`
    : isDm
      ? html`<small>${s.members[other.id]?.online ? 'Online' : 'Away'}</small>`
      : html`<small>${room?.members.length === 1 ? '1 member' : `${room?.members.length || 0} members`} · ${online} online</small>`

  const drop = (e) => {
    e.preventDefault()
    setDragging(false)
    ;[...(e.dataTransfer?.files || [])].forEach((f) => upload(f, conv, ''))
  }

  const items = []
  shown.forEach((m, i) => {
    const prev = shown[i - 1]
    if (!prev || !sameDay(prev.at, m.at)) items.push(html`<div class="day" key=${'d' + m.id}><span>${dayLabel(m.at)}</span></div>`)
    const first = !prev || prev.from !== m.from || m.at - prev.at > GROUP_GAP || !sameDay(prev.at, m.at)
    items.push(html`<${Message} key=${m.id} m=${m} first=${first} mine=${m.from === s.me.id}
      selected=${selected === m.id} onSelect=${(id) => setSelected(selected === id ? null : id)}
      replied=${m.replyTo ? byId.get(m.replyTo) : null}
      seen=${lastMine && m.id === lastMine.id ? seenLine(s, conv, m, c) : null} />`)
  })

  return html`<section class="chat" onDragOver=${(e) => { e.preventDefault(); setDragging(true) }}
      onDragLeave=${(e) => { if (e.currentTarget === e.target) setDragging(false) }} onDrop=${drop}>
    ${s.status === 'reconnecting' && html`<div class="banner" role="status">Reconnecting to ${s.serverName}…</div>`}
    <header class="chat-head">
      <button class="icon-btn back" aria-label="Back to conversations" onClick=${() => setState({ active: null })}>${Icon.back()}</button>
      <div class="title">
        <b>${room ? (room.locked ? Icon.lock(15) : Icon.hash()) : ''}<span>${convTitle(s, conv)}</span></b>
        ${subtitle}
      </div>
      ${room && html`<button class="icon-btn" aria-label="People in this room" aria-pressed=${s.showMembers} onClick=${() => setState({ showMembers: !s.showMembers })}>${Icon.users()}</button>`}
      ${room && room.id !== 'public' && html`<button class="icon-btn" aria-label="Room options" onClick=${(e) => setState({ menu: { kind: 'room', roomId: room.id, admin: isAdmin(s, room), x: e.currentTarget.getBoundingClientRect() } })}>${Icon.more()}</button>`}
    </header>
    <div class="messages" ref=${list} onScroll=${onScroll} onClick=${() => setSelected(null)} role="log" aria-label=${`Messages in ${convTitle(s, conv)}`}>
      ${c.messages.length > shown.length && html`<div class="earlier"><button class="btn ghost" onClick=${() => setState((st) => ({ limit: { ...st.limit, [conv]: limit + PAGE } }))}>Show earlier messages</button></div>`}
      ${c.loaded && c.messages.length === 0 && html`<div class="empty" style="min-height:60%"><div>
        <b>${isDm ? `Say hi to ${other.name}` : `Welcome to ${convTitle(s, conv)}`}</b>
        ${isDm ? 'Only the two of you can see this conversation.' : 'Messages here are seen by everyone in the room.'}
      </div></div>`}
      ${items}
    </div>
    ${newBelow && html`<button class="new-pill" onClick=${() => { const el = list.current; el.scrollTop = el.scrollHeight; setNewBelow(false) }}>New messages ↓</button>`}
    <${Composer} conv=${conv} />
    ${dragging && html`<div class="drop">Drop to send</div>`}
  </section>`
}

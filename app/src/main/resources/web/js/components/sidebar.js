import { html } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { Avatar } from './avatar.js'
import { colorOf, plain } from '../format.js'
import { dmOther, person, setState, useStore } from '../store.js'
import { openConv, send } from '../socket.js'

function Embers({ room, s }) {
  const online = room.members.map((id) => s.members[id]).filter((m) => m && m.online)
  const typing = Object.values(s.typing['room:' + room.id] || {}).some((t) => t > Date.now())
  if (!online.length) return null
  const shown = online.slice(0, 5)
  return html`<span class=${'embers' + (typing ? ' typing' : '')} title=${online.map((m) => m.name).join(', ')}>
    ${shown.map((m) => html`<i style=${{ background: colorOf(m.color) }}></i>`)}
    ${online.length > shown.length && html`<small>+${online.length - shown.length}</small>`}
  </span>`
}

export function Sidebar() {
  const s = useStore()
  const me = s.me
  const rooms = [...s.rooms].sort((a, b) => (a.id === 'public' ? -1 : b.id === 'public' ? 1 : a.name.localeCompare(b.name)))
  const dms = Object.keys(s.convs)
    .filter((c) => c.startsWith('dm:'))
    .map((c) => ({ conv: c, other: person(s, dmOther(s, c)), last: s.convs[c].messages.at(-1) }))
    .sort((a, b) => (b.last?.at || 0) - (a.last?.at || 0))
  const dmWith = new Set(dms.map((d) => d.other.id))
  const people = Object.values(s.members).filter((m) => m.id !== me.id && m.online && !dmWith.has(m.id))

  const openRoom = (room) => {
    const conv = 'room:' + room.id
    if (room.members.includes(me.id)) return openConv(conv)
    if (room.locked && !me.isHost && room.creatorId !== me.id) return setState({ dialog: { kind: 'password', roomId: room.id } })
    send({ type: 'join_room', roomId: room.id })
  }

  return html`<aside class="sidebar" aria-label="Conversations">
    <div class="side-head">
      <img src="icon.svg" alt="" />
      <div class="title"><b>${s.serverName}</b><span>${Object.values(s.members).filter((m) => m.online).length} here now</span></div>
      <button class="icon-btn" aria-label="Menu" onClick=${(e) => setState({ menu: { kind: 'main', x: e.currentTarget.getBoundingClientRect() } })}>${Icon.more()}</button>
    </div>
    <div class="side-scroll">
      <div class="section-head"><h2>Rooms</h2>
        <button class="icon-btn" aria-label="Create a room" onClick=${() => setState({ dialog: { kind: 'createRoom' } })}>${Icon.plus()}</button>
      </div>
      ${rooms.map((room) => {
        const conv = 'room:' + room.id
        const joined = room.members.includes(me.id)
        const unread = s.convs[conv]?.unread || 0
        return html`<button class=${`row${s.active === conv ? ' active' : ''}${joined ? '' : ' unjoined'}${unread ? ' unread' : ''}`}
          onClick=${() => openRoom(room)} aria-current=${s.active === conv ? 'page' : undefined}>
          <span class="glyph">${room.locked ? Icon.lock() : Icon.hash()}</span>
          <span class="name">${room.name}</span>
          <${Embers} room=${room} s=${s} />
          ${unread > 0 && html`<span class="badge" aria-label=${`${unread} unread`}>${unread > 99 ? '99+' : unread}</span>`}
        </button>`
      })}

      ${dms.length > 0 && html`<div class="section-head"><h2>Direct messages</h2></div>`}
      ${dms.map(({ conv, other, last }) => {
        const unread = s.convs[conv]?.unread || 0
        return html`<button class=${`row${s.active === conv ? ' active' : ''}${unread ? ' unread' : ''}`} onClick=${() => openConv(conv)}>
          <${Avatar} who=${other} size=${28} online=${s.members[other.id]?.online} />
          <span class="name">${other.name}${last && html`<small class="hint" style="display:block;font-weight:400">${last.deleted ? 'Message deleted' : last.attachment && !last.text ? last.attachment.name : plain(last.text, 40)}</small>`}</span>
          ${unread > 0 && html`<span class="badge">${unread}</span>`}
        </button>`
      })}

      ${people.length > 0 && html`<div class="section-head"><h2>People here</h2></div>`}
      ${people.map((m) => html`<button class="row" onClick=${() => send({ type: 'open_dm', memberId: m.id })} title=${`Message ${m.name}`}>
        <${Avatar} who=${m} size=${28} online />
        <span class="name">${m.name}</span>
        ${m.isHost && html`<span class="host-tag">Host</span>`}
      </button>`)}
    </div>
    <div class="me-bar">
      <${Avatar} who=${me} size=${32} online />
      <span class="name">${me.name}</span>
      ${me.isHost && html`<span class="host-tag">Host</span>`}
    </div>
  </aside>`
}

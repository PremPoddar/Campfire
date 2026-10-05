import { html } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { Avatar } from './avatar.js'
import { isAdmin, person, roomOf, setState, useStore } from '../store.js'
import { send } from '../socket.js'

export function Members() {
  const s = useStore()
  const room = roomOf(s, s.active)
  if (!room) return null
  const admin = isAdmin(s, room)
  const people = room.members.map((id) => person(s, id))
    .sort((a, b) => (s.members[b.id]?.online ? 1 : 0) - (s.members[a.id]?.online ? 1 : 0) || a.name.localeCompare(b.name))

  return html`<aside class="members" aria-label="People in this room">
    <div class="side-head">
      <div class="title"><b>People</b><span>${room.members.length} in ${room.name}</span></div>
      <button class="icon-btn" aria-label="Close" onClick=${() => setState({ showMembers: false })}>${Icon.x()}</button>
    </div>
    <div class="side-scroll">
      ${people.map((p) => {
        const online = !!s.members[p.id]?.online
        const self = p.id === s.me.id
        return html`<div class=${'person' + (online ? '' : ' offline')}>
          <${Avatar} who=${p} size=${32} online=${online} />
          <span class="name">${p.name}${self ? ' (you)' : ''}</span>
          ${p.isHost && html`<span class="host-tag">Host</span>`}
          ${!self && html`<span class="acts">
            <button class="icon-btn" aria-label=${`Message ${p.name}`} onClick=${() => send({ type: 'open_dm', memberId: p.id })}>${Icon.chat()}</button>
            ${admin && !p.isHost && room.id !== 'public' && html`<button class="icon-btn" aria-label=${`Remove ${p.name} from ${room.name}`}
              onClick=${() => setState({ dialog: { kind: 'confirm', title: `Remove ${p.name}?`, body: `${p.name} leaves ${room.name} and can't come back while this Campfire runs.`, action: 'Remove', cmd: { type: 'remove_member', roomId: room.id, memberId: p.id } } })}>${Icon.door()}</button>`}
            ${s.me.isHost && !p.isHost && html`<button class="icon-btn" aria-label=${`Remove ${p.name} from Campfire`}
              onClick=${() => setState({ dialog: { kind: 'confirm', title: `Remove ${p.name} from Campfire?`, body: `They're disconnected and their device can't rejoin until you start a new Campfire.`, action: 'Remove from Campfire', cmd: { type: 'ban', memberId: p.id } } })}>${Icon.ban()}</button>`}
          </span>`}
        </div>`
      })}
    </div>
  </aside>`
}

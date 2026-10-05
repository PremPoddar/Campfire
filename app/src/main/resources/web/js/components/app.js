import { html } from '../../lib/preact-htm.js'
import { Join, Ended } from './join.js'
import { Sidebar } from './sidebar.js'
import { Chat } from './chat.js'
import { Members } from './members.js'
import { Dialogs, Lightbox, Menus, Toasts } from './dialogs.js'
import { roomOf, useStore } from '../store.js'

export function App() {
  const s = useStore()
  if (s.status === 'loading' || (s.status === 'connecting' && !s.me)) return html`<main class="center"><p class="hint">Connecting…</p></main>`
  if (s.status === 'joining') return html`<${Join} />`
  if (s.status === 'ended') return html`<${Ended} title="This Campfire has ended" body=${`${s.serverName} was closed by its host. Everything that was shared is gone.`} />`
  if (s.status === 'removed') return html`<${Ended} title="You were removed" body="The host removed you from this Campfire." />`
  if (!s.me) return html`<main class="center"><p class="hint">Reconnecting…</p></main>`
  const members = s.showMembers && !!roomOf(s, s.active)
  return html`<div class=${`shell${members ? ' with-members' : ''}${s.active ? ' chat-open' : ''}`}>
    <${Sidebar} />
    <${Chat} />
    ${members && html`<${Members} />`}
    <${Dialogs} /><${Menus} /><${Lightbox} /><${Toasts} />
  </div>`
}

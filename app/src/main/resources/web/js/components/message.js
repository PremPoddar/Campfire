import { html } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { Avatar } from './avatar.js'
import { colorOf, fileUrl, formatSize, plain, renderText, time } from '../format.js'
import { getState, isAdmin, person, roomOf, setState, toast } from '../store.js'
import { send } from '../socket.js'

export const QUICK = ['👍', '❤️', '😂', '😮', '😢', '🔥']

function Attachment({ att }) {
  const url = fileUrl(att)
  if (att.mime.startsWith('image/')) {
    return html`<img class="media" src=${url} alt=${att.name} loading="lazy" onClick=${(e) => { e.stopPropagation(); setState({ lightbox: att }) }} />`
  }
  if (att.mime.startsWith('video/')) return html`<video class="media" src=${url} controls preload="metadata" playsinline></video>`
  if (att.mime.startsWith('audio/')) return html`<audio src=${url} controls preload="metadata" style="max-width:100%"></audio>`
  return html`<a class="file" href=${url} download=${att.name} onClick=${(e) => e.stopPropagation()}>
    <span class="glyph">${Icon.file()}</span>
    <span style="min-width:0"><b>${att.name}</b><small>${formatSize(att.size)} · Download</small></span>
  </a>`
}

// A poll: tap a choice to vote, again to take it back; the bars show how it stands.
function Poll({ m, me }) {
  const p = m.poll
  const total = Object.values(p.votes || {}).reduce((n, ids) => n + ids.length, 0)
  const mine = Object.entries(p.votes || {}).find(([, ids]) => ids.includes(me))?.[0]
  return html`<div class="poll" onClick=${(e) => e.stopPropagation()}>
    <b>${p.question}</b>
    ${p.options.map((o, i) => {
      const n = (p.votes?.[i] || []).length
      const share = total ? Math.round((n / total) * 100) : 0
      return html`<button class=${'opt' + (String(i) === mine ? ' mine' : '')} onClick=${() => send({ type: 'vote', msgId: m.id, option: i })}>
        <i style=${{ width: share + '%' }}></i><span>${o}</span><span>${n}</span></button>`
    })}
    <small>${total === 1 ? '1 vote' : `${total} votes`}</small>
  </div>`
}

function Preview({ p }) {
  return html`<a class="preview" href=${p.url} target="_blank" rel="noopener noreferrer" onClick=${(e) => e.stopPropagation()}>
    ${p.site && html`<small>${p.site}</small>`}
    ${p.title && html`<b>${p.title}</b>`}
    ${p.description && html`<span>${p.description}</span>`}
    ${p.imageId && html`<img src=${`/files/${p.imageId}/preview`} alt="" loading="lazy" />`}
  </a>`
}

function jumpTo(id) {
  const el = document.getElementById('m' + id)
  if (!el) { toast('That message is too far back.'); return }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  const bubble = el.querySelector('.bubble')
  bubble?.classList.remove('flash')
  void bubble?.offsetWidth
  bubble?.classList.add('flash')
}

export function Message({ m, first, mine, selected, onSelect, seen, replied }) {
  const s = getState()
  const who = person(s, m.from)
  const room = roomOf(s, m.conv)
  const canDelete = mine || s.me.isHost || isAdmin(s, room)

  const react = (emoji) => send({ type: 'react', msgId: m.id, emoji })
  const tools = !m.deleted && html`<div class="tools" role="toolbar" aria-label="Message actions" onClick=${(e) => e.stopPropagation()}>
    ${QUICK.slice(0, 3).map((e) => html`<button aria-label=${`React ${e}`} onClick=${() => react(e)}>${e}</button>`)}
    <button aria-label="More reactions" onClick=${(e) => setState({ menu: { kind: 'emoji', msgId: m.id, x: e.currentTarget.getBoundingClientRect() } })}>${Icon.smile()}</button>
    <button aria-label="Reply" onClick=${() => { setState({ replyTo: m, editing: null }); document.getElementById('composer')?.focus() }}>${Icon.reply()}</button>
    <button aria-label="More" onClick=${(e) => setState({ menu: { kind: 'message', msg: m, mine, canDelete, canPin: !room || s.me.isHost || isAdmin(s, room), x: e.currentTarget.getBoundingClientRect() } })}>${Icon.more()}</button>
  </div>`

  return html`<div id=${'m' + m.id} class=${`msg${first ? ' first' : ''}${mine ? ' mine' : ''}${selected ? ' selected' : ''}`} onClick=${() => onSelect(m.id)}>
    <div class="avatar-slot">${!mine && first && html`<${Avatar} who=${who} size=${34} />`}</div>
    <div class="stack">
      ${!mine && first && html`<div class="sender" style=${{ color: colorOf(who.color) }}>${who.name}${who.isHost ? ' · Host' : ''}</div>`}
      ${tools}
      ${m.deleted
        ? html`<div class="bubble deleted">Message deleted</div>`
        : html`<div class="bubble">
          ${replied && html`<button class="quote" onClick=${(e) => { e.stopPropagation(); jumpTo(replied.id) }}>
            <b>${person(s, replied.from).name}</b><span>${replied.deleted ? 'Message deleted' : plain(replied.text) || replied.attachment?.name}</span>
          </button>`}
          ${m.pinned && html`<div class="pinned-tag">${Icon.pin(12)} Pinned</div>`}
          ${m.attachment && html`<${Attachment} att=${m.attachment} />`}
          ${m.poll && html`<${Poll} m=${m} me=${s.me.id} />`}
          ${m.text && html`<div class="text" dangerouslySetInnerHTML=${{ __html: renderText(m.text) }}></div>`}
          ${m.preview && html`<${Preview} p=${m.preview} />`}
          <div class="meta">${m.editedAt && html`<span>edited</span>`}<time datetime=${new Date(m.at).toISOString()}>${time(m.at)}</time></div>
        </div>`}
      ${Object.keys(m.reactions || {}).length > 0 && html`<div class="reactions">
        ${Object.entries(m.reactions).map(([emoji, ids]) => html`<button class=${'chip' + (ids.includes(s.me.id) ? ' mine' : '')}
          title=${ids.map((id) => person(s, id).name).join(', ')} onClick=${(e) => { e.stopPropagation(); react(emoji) }}>
          ${emoji} <span>${ids.length}</span></button>`)}
      </div>`}
      ${seen && html`<div class="seen" title=${seen.title}>${seen.text}</div>`}
    </div>
  </div>`
}

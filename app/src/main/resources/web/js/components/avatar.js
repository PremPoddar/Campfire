import { html } from '../../lib/preact-htm.js'
import { colorOf, initials } from '../format.js'

export function Avatar({ who, size = 34, online = false }) {
  return html`<span class="avatar" style=${{ width: size + 'px', height: size + 'px', background: colorOf(who.color), fontSize: Math.round(size * 0.38) + 'px' }} aria-hidden="true">
    ${initials(who.name)}${online && html`<span class="dot"></span>`}
  </span>`
}

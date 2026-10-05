import { html, useState, useRef, useEffect } from '../../lib/preact-htm.js'
import { COLORS } from '../format.js'
import { join, isHostDevice } from '../socket.js'
import { unlockAudio } from '../attention.js'
import { load, save, useStore } from '../store.js'

export function Join() {
  const s = useStore()
  const [name, setName] = useState(load('campfire.name') || '')
  const [color, setColor] = useState(() => Number(load('campfire.color') ?? Math.floor(Math.random() * COLORS.length)))
  const [busy, setBusy] = useState(false)
  const input = useRef()
  useEffect(() => input.current?.focus(), [])
  useEffect(() => { if (s.joinError) setBusy(false) }, [s.joinError])

  const submit = (e) => {
    e.preventDefault()
    const clean = name.trim()
    if (!clean) return
    save('campfire.name', clean)
    save('campfire.color', String(color))
    unlockAudio()
    setBusy(true)
    join(clean, color)
  }

  return html`<main class="center">
    <form class="card" onSubmit=${submit}>
      <div class="brand">
        <img src="icon.svg" alt="" />
        <div><h1>${s.serverName}</h1><p>${isHostDevice ? "You're hosting this Campfire" : 'Chat with people on this network'}</p></div>
      </div>
      <label class="label" for="name">Your name</label>
      <input id="name" ref=${input} class="field" maxlength="32" autocomplete="nickname" value=${name}
        onInput=${(e) => setName(e.target.value)} placeholder="What should people call you?" />
      <div class="error-text" role="alert">${s.joinError}</div>
      <span class="label" id="colour-label">Colour</span>
      <div class="swatches" role="radiogroup" aria-labelledby="colour-label">
        ${COLORS.map((c, i) => html`<button type="button" class="swatch" role="radio" aria-checked=${i === color}
          aria-label=${`Colour ${i + 1}`} style=${{ background: c }} onClick=${() => setColor(i)}></button>`)}
      </div>
      <button class="btn" style="width:100%" disabled=${!name.trim() || busy}>${busy ? 'Joining…' : 'Join'}</button>
      <p class="privacy">No account needed. Everything disappears when the host ends this Campfire.</p>
    </form>
  </main>`
}

export function Ended({ title, body }) {
  return html`<main class="center">
    <div class="card">
      <div class="brand"><img src="icon.svg" alt="" /><div><h1>${title}</h1></div></div>
      <p class="hint" style="margin:0 0 18px">${body}</p>
      <button class="btn ghost" onClick=${() => location.reload()}>Try again</button>
    </div>
  </main>`
}

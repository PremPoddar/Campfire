import { html, useEffect, useRef, useState } from '../../lib/preact-htm.js'
import { Icon } from '../icons.js'
import { plain } from '../format.js'
import { person, setState, useStore } from '../store.js'
import { send, upload } from '../socket.js'

const touch = matchMedia('(hover: none)').matches
let lastTyping = 0

export function Composer({ conv }) {
  const s = useStore()
  const [text, setText] = useState('')
  const area = useRef()
  const fileInput = useRef()
  const drafts = useRef({})

  // Keep a draft per conversation.
  useEffect(() => {
    setText(drafts.current[conv] || '')
    // Ready to type as soon as a conversation opens (not on phones, where it would pop the keyboard).
    if (!touch) area.current?.focus()
  }, [conv])
  useEffect(() => {
    if (s.editing) { setText(s.editing.text); area.current?.focus() }
  }, [s.editing])
  useEffect(() => {
    const el = area.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 180) + 'px'
  }, [text])

  const change = (value) => {
    setText(value)
    if (!s.editing) drafts.current[conv] = value
    if (value && Date.now() - lastTyping > 2000) {
      lastTyping = Date.now()
      send({ type: 'typing', conv })
    }
  }

  const submit = () => {
    const value = text.trim()
    if (s.editing) {
      if (value && value !== s.editing.text) send({ type: 'edit', msgId: s.editing.id, text: value })
      setState({ editing: null })
      setText(drafts.current[conv] || '')
      return
    }
    if (!value) return
    const clientId = Math.random().toString(36).slice(2)
    send({ type: 'send', conv, text: value, replyTo: s.replyTo?.id ?? null, clientId })
    setState((st) => ({ replyTo: null, pending: { ...st.pending, [clientId]: true } }))
    drafts.current[conv] = ''
    setText('')
    lastTyping = 0
  }

  const keyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !touch && !e.isComposing) { e.preventDefault(); submit() }
    if (e.key === 'Escape') setState({ replyTo: null, editing: null })
    if (e.key === 'ArrowUp' && !text) {
      const mine = s.convs[conv]?.messages.filter((m) => m.from === s.me.id && !m.deleted && m.text).at(-1)
      if (mine) { e.preventDefault(); setState({ editing: mine, replyTo: null }) }
    }
  }

  const pickFiles = (files) => {
    const list = [...files]
    list.forEach((f, i) => upload(f, conv, i === 0 ? text.trim() : ''))
    if (list.length && text.trim()) { drafts.current[conv] = ''; setText('') }
  }

  const paste = (e) => {
    const files = [...(e.clipboardData?.files || [])]
    if (files.length) { e.preventDefault(); pickFiles(files) }
  }

  const uploads = s.uploads.filter((u) => u.conv === conv)
  const context = s.editing
    ? html`<div class="context"><span><b>Editing message</b></span><button class="icon-btn" aria-label="Cancel editing" onClick=${() => { setState({ editing: null }); setText(drafts.current[conv] || '') }}>${Icon.x(16)}</button></div>`
    : s.replyTo && s.replyTo.conv === conv
      ? html`<div class="context"><span>Replying to <b>${person(s, s.replyTo.from).name}</b>: ${plain(s.replyTo.text, 80) || s.replyTo.attachment?.name}</span><button class="icon-btn" aria-label="Cancel reply" onClick=${() => setState({ replyTo: null })}>${Icon.x(16)}</button></div>`
      : null

  return html`<div class="composer">
    ${uploads.length > 0 && html`<div class="uploads">${uploads.map((u) => html`<div class="upload">
      <span class="nm">${u.name}</span>
      <span class="bar"><i style=${{ width: Math.round(u.progress * 100) + '%' }}></i></span>
      <span>${Math.round(u.progress * 100)}%</span>
      <button class="icon-btn" aria-label=${`Cancel ${u.name}`} onClick=${() => u.xhr.abort()}>${Icon.x(16)}</button>
    </div>`)}</div>`}
    <${TypingLine} conv=${conv} />
    ${context}
    <div class="box">
      <button class="icon-btn" aria-label="Attach files" onClick=${() => fileInput.current.click()}>${Icon.clip()}</button>
      <input ref=${fileInput} type="file" multiple hidden onChange=${(e) => { pickFiles(e.target.files); e.target.value = '' }} />
      <textarea id="composer" ref=${area} rows="1" value=${text} placeholder="Message" aria-label="Message"
        onInput=${(e) => change(e.target.value)} onKeyDown=${keyDown} onPaste=${paste} maxlength="4000"></textarea>
      <button class="send" aria-label=${s.editing ? 'Save edit' : 'Send'} disabled=${!text.trim()} onClick=${submit}>${Icon.send()}</button>
    </div>
  </div>`
}

function TypingLine({ conv }) {
  const s = useStore()
  const now = Date.now()
  const names = Object.entries(s.typing[conv] || {}).filter(([id, t]) => t > now && id !== s.me.id).map(([id]) => person(s, id).name)
  const text = names.length === 0 ? '' : names.length === 1 ? `${names[0]} is typing…` : names.length === 2 ? `${names[0]} and ${names[1]} are typing…` : 'Several people are typing…'
  return html`<div class="typing-line" aria-live="polite">${text}</div>`
}

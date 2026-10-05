// Turning message text into safe HTML, and small display helpers.

export const COLORS = ['#E5484D', '#F76B15', '#C99400', '#3E9B4F', '#12A594', '#0090FF', '#3E63DD', '#8E4EC6', '#D6409F', '#7C7C74']
export const colorOf = (i) => COLORS[(i ?? 0) % COLORS.length]

export function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

const URL_RE = /\bhttps?:\/\/[^\s<>"']+/gi

function linkify(escaped) {
  return escaped.replace(URL_RE, (raw) => {
    // Leave trailing punctuation outside the link.
    const m = raw.match(/^(.*?)([.,;:!?)\]}]*)$/)
    const url = m[1], tail = m[2]
    return `<a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>${tail}`
  })
}

function inline(escaped) {
  // Inline code first so its contents are never formatted.
  const parts = escaped.split(/(`[^`\n]+`)/g)
  return parts.map((p, i) => {
    if (i % 2 === 1) return `<code>${p.slice(1, -1)}</code>`
    return linkify(p)
      .replace(/\*\*(?=\S)([^*]+?)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[\s(])\*(?=\S)([^*\n]+?)\*(?=$|[\s.,!?)])/g, '$1<em>$2</em>')
  }).join('')
}

/** Escaped text with **bold**, *italic*, `code`, ``` blocks ``` and links. Safe for innerHTML. */
export function renderText(text) {
  const blocks = escapeHtml(text).split(/```(?:[a-z0-9]*\n)?([\s\S]*?)```/g)
  return blocks.map((b, i) => (i % 2 === 1 ? `<pre><code>${b.replace(/\n$/, '')}</code></pre>` : inline(b))).join('')
}

export function plain(text, max = 120) {
  const t = text.replace(/```[\s\S]*?```/g, '[code]').replace(/[*`]/g, '').replace(/\s+/g, ' ').trim()
  return t.length > max ? t.slice(0, max - 1) + '…' : t
}

export function initials(name = '?') {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0] || '?').slice(0, 2)
  return letters.toUpperCase()
}

export function formatSize(n) {
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = n / 1024, u = 0
  while (v >= 1024 && u < units.length - 1) { v /= 1024; u++ }
  return `${v < 10 ? v.toFixed(1) : Math.round(v)} ${units[u]}`
}

export const time = (at) => new Date(at).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

export function dayLabel(at) {
  const d = new Date(at), today = new Date()
  const start = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime()
  const diff = Math.round((start(today) - start(d)) / 86400000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Yesterday'
  return d.toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' })
}

export const sameDay = (a, b) => new Date(a).toDateString() === new Date(b).toDateString()

export const fileUrl = (att) => `/files/${att.id}/${encodeURIComponent(att.name)}`

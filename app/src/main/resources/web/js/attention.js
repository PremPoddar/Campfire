// Unread count in the tab title, a dot on the favicon and a soft sound.
import { convTitle, getState } from './store.js'

export function refreshTitle() {
  const s = getState()
  const unread = Object.values(s.convs).reduce((n, c) => n + (c.unread || 0), 0)
  const where = s.active && s.status === 'ready' ? `${convTitle(s, s.active)} · ` : ''
  document.title = `${unread ? `(${unread}) ` : ''}${where}${s.serverName}`
  const icon = document.getElementById('favicon')
  if (icon) icon.href = unread ? 'icon-unread.svg' : 'icon.svg'
}

let audio = null
export function unlockAudio() {
  if (audio) return
  try { audio = new (window.AudioContext || window.webkitAudioContext)() } catch (e) {}
}

export function playPop() {
  if (!audio) return
  const t = audio.currentTime
  const osc = audio.createOscillator()
  const gain = audio.createGain()
  osc.type = 'sine'
  osc.frequency.setValueAtTime(880, t)
  osc.frequency.exponentialRampToValueAtTime(520, t + 0.12)
  gain.gain.setValueAtTime(0.0001, t)
  gain.gain.exponentialRampToValueAtTime(0.18, t + 0.01)
  gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.22)
  osc.connect(gain).connect(audio.destination)
  osc.start(t)
  osc.stop(t + 0.25)
}

import { getState, setState, subscribe } from './store.js'

/*
 * Makes the phone's back gesture work inside Campfire, like an app: back closes a dialog or menu,
 * then the member list, then the open conversation (returning to the chat list) — and only leaves
 * the page from the chat list. One history entry stands for "something is open".
 */
const open = (s) => !!(s.dialog || s.menu || s.showMembers || s.active)
let pushed = false
let ignoreNextPop = false

function closeTop() {
  const s = getState()
  if (s.dialog) setState({ dialog: null })
  else if (s.menu) setState({ menu: null })
  else if (s.showMembers) setState({ showMembers: false })
  else if (s.active) setState({ active: null, replyTo: null, editing: null })
}

export function startNav() {
  subscribe(() => {
    const s = getState()
    if (s.status !== 'ready') return
    if (open(s) && !pushed) {
      history.pushState({ campfire: true }, '')
      pushed = true
    } else if (!open(s) && pushed) {
      // Closed from the page itself: drop our entry so back still leaves from the list.
      pushed = false
      ignoreNextPop = true
      history.back()
    }
  })
  addEventListener('popstate', () => {
    if (ignoreNextPop) { ignoreNextPop = false; return }
    pushed = false
    closeTop()
    // More still open (say a dialog over a conversation): stand ready for the next back.
    if (open(getState())) { history.pushState({ campfire: true }, ''); pushed = true }
  })
}

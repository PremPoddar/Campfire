import { html, render } from '../lib/preact-htm.js'
import { App } from './components/app.js'
import { boot } from './socket.js'
import { startNav } from './nav.js'

render(html`<${App} />`, document.getElementById('app'))
boot()
startNav()

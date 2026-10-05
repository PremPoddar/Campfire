<div align="center">

<img src="app/src/main/resources/web/icon.svg" width="96" alt="Campfire">

# Campfire

**A chat room for everyone nearby.**

One phone hosts. Everyone else opens a link. No accounts, no internet, nothing left behind.

![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-3-087CFA?logo=ktor&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

</div>

---

Start a Campfire on your phone and it shows a link and a QR code. Anyone on the same Wi-Fi, or
connected to your hotspot, opens it in any browser, picks a name and is chatting. When you end
it, every message, room and file is gone.

## What it does

| | |
|---|---|
| 🔥 **Public room** | Everyone lands in Public. It's always there. |
| 🔒 **Rooms** | Anyone can start a room, optionally with a password. |
| 💬 **Direct messages** | Private one-to-one chats with anyone here. |
| ↩️ **Replies & reactions** | Quote a message, react with emoji, edit or delete your own. |
| 👀 **Read receipts** | "Seen by 3" in rooms, Sent / Seen in direct messages. |
| ✍️ **Typing & presence** | Who's typing, who's online, and glowing dots showing who is in each room. |
| 📎 **Files** | Any file, any size. Photos and videos play inline. |
| 🔗 **Link previews** | The host phone fetches titles and pictures so everyone sees them, even offline. |
| 🛡️ **Moderation** | Room creators remove people from their rooms; the host can remove anyone. |
| 🌗 **Light & dark** | In the app and in the browser. |

## How it works

```
 host phone                                       anyone on the network
┌──────────────────────────────────────┐         ┌───────────────────────┐
│ Campfire app                          │  http   │ any browser           │
│  ├─ foreground service                │◀───────▶│  index.html + ES      │
│  │   └─ Ktor CIO server :8080         │   ws    │  modules (Preact+htm) │
│  │        ├─ /       web client       │         └───────────────────────┘
│  │        ├─ /ws     chat (JSON)      │
│  │        ├─ /api/upload, /files/…    │
│  │        └─ Hub ─▶ ChatState (rules) │
│  └─ host's own chat in a WebView      │
└──────────────────────────────────────┘
```

- `core/ChatState` holds every rule (rooms, passwords, admins, direct messages, edits,
  reactions, receipts, typing) as plain Kotlin. Each command returns the events to send and
  who gets them, so the rules are unit tested without sockets.
- `server/Hub` runs commands one at a time under a lock and queues events per socket.
- Uploads stream straight to disk over HTTP, so a big file never blocks the chat. Videos are
  served with byte ranges so they seek.
- The browser client has no build step: Preact + htm are vendored, the font is bundled, and
  nothing is loaded from the internet.

## Privacy, honestly

- Nothing is stored after a Campfire ends. Files live in the app's cache and are wiped on
  start and stop.
- It is **not encrypted**. Browsers only allow encryption and HTTPS-only features on secure
  pages, and a local network can't have a trusted certificate without warnings. Someone
  watching the same Wi-Fi could read messages and room passwords, and direct messages pass
  through the host phone. The app and the web chat both say this plainly.
- Link previews are fetched by the host phone. It refuses any address on the local network,
  so a link can't be used to probe the router or other devices.
- Uploaded files are never served as web pages or scripts.

## Building

Requires JDK 17 or 21 for Gradle.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Working on the web client

```
CAMPFIRE_DEV=1 ./gradlew testDebugUnitTest --tests '*DevServer*'
```

starts a real Campfire on http://localhost:8080 that serves `app/src/main/resources/web`
straight from disk; reload the page to see changes. Open a second browser profile (or
http://127.0.0.1:8080) to chat with yourself.

## License

Apache 2.0. Figtree is used under the SIL Open Font License. Preact and htm are MIT licensed.

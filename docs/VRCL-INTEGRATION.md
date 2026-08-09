# VRChat Legends integration

Sign-in is **optional**. Every OSC feature works without an account; the login only adds
your profile name to the status line and the event feed to the chatbox.

## How the login works

The website's OAuth start route already handles app deep links, so no backend change was
needed. In `backend/src/routes/oauth.js`:

```js
function isAppDeepLink(returnTo) {
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(String(returnTo || ''))
}
// ...
if (isAppDeepLink(returnTo)) {
  const sep = returnTo.includes('?') ? '&' : '?'
  return res.redirect(`${returnTo}${sep}${tokenQs}`)   // session_token=<jwt>
}
```

So the flow is:

```
App  ──► Custom Tab
         https://vrchatlegends.com/api/oauth/discord/start?returnTo=vrcoscc%3A%2F%2Fauth
              │
              ├─ user authenticates with Discord / Google / GitHub / Twitch
              ├─ (2FA page on the website if the account has it enabled)
              ▼
         302 vrcoscc://auth?session_token=<jwt>
              │
App  ◄────────┘   MainActivity intent-filter catches it, stores the JWT
```

The app never sees a password. The JWT is stored in the app's private DataStore and sent as
`Authorization: Bearer <jwt>`.

### Fallback

If a locked-down browser blocks the custom scheme, the Account screen accepts a personal
API key from **Account settings → API keys** on the website. Those start with `vrcl_` and
the backend accepts them on the same `Authorization: Bearer` header (see
`backend/src/middleware/optionalApiKey.js`).

## Endpoints used

| Method | Path | Used for |
| --- | --- | --- |
| `GET` | `/api/oauth/providers` | which providers are configured (optional) |
| `GET` | `/api/oauth/<provider>/start?returnTo=...` | begins the Custom Tab flow |
| `GET` | `/api/auth/me` | profile after sign-in |
| `GET` | `/api/events?limit=5` | event feed, pushed to the chatbox on tap |
| `POST` | `/api/auth/logout` | sign out |

Response parsing is deliberately lenient: `me` accepts `{account}`, `{user}` or a bare
object, and `events` accepts a bare array, `{events:[...]}` or `{data:[...]}`. That keeps
the app from breaking when the API shape shifts.

## Configuration

Set in `app/build.gradle.kts` under `defaultConfig`:

```kotlin
buildConfigField("String", "VRCL_BASE_URL", "\"https://vrchatlegends.com\"")
buildConfigField("String", "AUTH_REDIRECT_SCHEME", "\"vrcoscc\"")
```

The scheme also has to match the `<data android:scheme="vrcoscc" android:host="auth" />`
entry in `AndroidManifest.xml`.

To test against a local backend, change `VRCL_BASE_URL` to your machine's LAN IP. The app
already declares `usesCleartextTraffic` so plain HTTP works for development.

## Contact

All contact and support goes through Discord: <https://discord.gg/6xPkZ7Dxp9>

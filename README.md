# Content Hub — Assets View extension

An Adobe App Builder UI extension that adds a **"Content Hub"** button to the AEM
Assets View ActionBar when a **folder** is selected. It opens a modal where the
user can:

- **Approve / Disapprove** the content (`dam:status` = `approved` / `nostatus`)
- Choose the **activation target** — Dynamic Media & Content Hub (`dam:activationTarget=delivery`)
  or Content Hub only (`dam:activationTarget=contenthub`)
- Optionally apply the change **recursively** to sub-folders

On confirm, the request is relayed to an AEM backend that runs the change as an
**asynchronous Sling Job** over every `dam:Asset` under the selected folder. The
UI is localized (en, fr, de, es).

> ⚠️ **Backend dependency (required).** This extension does nothing on its own.
> All business logic lives in the AEM project (servlet + Sling Job + OSGi
> configs), deployed through Adobe Cloud Manager. See **[BACKEND.md](./BACKEND.md)**
> for the two-repo architecture, the HTTP contract, and the reference mirror in
> [`aem-backend/`](./aem-backend/).

## Architecture

The browser never calls AEM directly (cross-origin CORS is rejected). Instead the
modal calls a same-origin App Builder Runtime action, which forwards the request to
AEM server-to-server:

```
Browser ──POST (same-origin)──▶ actions/contenthub ──POST (server-to-server)──▶ AEM servlet
```

| Piece | Location |
|---|---|
| ActionBar button + modal (React Spectrum) | `src/aem-assets-assetsview-1/web-src/` (this repo) |
| Runtime proxy action | `actions/contenthub/` (this repo) |
| Servlet, Sling Job, OSGi configs | AEM project (Cloud Manager) — see [BACKEND.md](./BACKEND.md) |

## Prerequisites

- Node.js 18+ and the [`aio` CLI](https://developer.adobe.com/app-builder/docs/get_started/)
- An App Builder project/workspace with **AEM Assets View UI extensibility** enabled
  (requires Assets Ultimate; enablement is done via an Adobe support case)
- The AEM backend deployed to the target environment (see [BACKEND.md](./BACKEND.md))

## Setup

- Populate the `.env` file in the project root as shown [below](#env)
- Install dependencies: `npm install`

## Local dev

- `aio app run` starts the local dev server (defaults to `localhost:9080`)

By default the UI is served locally while actions are deployed and served from
Adobe I/O Runtime. To run actions locally too, use `aio app dev`. See the
[difference between `aio app run` and `aio app dev`](https://developer.adobe.com/app-builder/docs/guides/development/#aio-app-dev-vs-aio-app-run).

To test the extension inside the AEM Assets View, open the Assets View in dev mode:

```
https://experience.adobe.com/?devMode=true#/@<org>/assets/browse/content/dam
```

## Test & coverage

- `aio app test` runs unit tests for the UI and actions
- `aio app test --e2e` runs the e2e tests

## Deploy & cleanup

- `aio app deploy` builds and deploys all actions to Runtime and the static files to the CDN
- `aio app undeploy` removes the app

> Deploy the **AEM backend first** (Cloud Manager Full Stack Pipeline), then
> `aio app deploy` for this extension. Until the backend is live, the proxy relays
> correctly but AEM returns `404` (the servlet does not exist yet).

## Configuration

### `.env`

Generate this file with `aio app use`. It must **not** be committed (it is
`.gitignore`d).

```bash
## Adobe I/O Runtime credentials
# AIO_RUNTIME_AUTH=
# AIO_RUNTIME_NAMESPACE=
```

### `app.config.yaml`

Main configuration file that defines the application's implementation, including
the `aem/assets/browse/1` extension point and the `contenthub` Runtime action.
More on [application and extension configuration](https://developer.adobe.com/app-builder/docs/guides/configuration/#appconfigyaml).

## Project layout

```
actions/
  contenthub/          Runtime proxy action (browser -> action -> AEM)
  generic/, publish-events/   aio scaffolding actions
src/aem-assets-assetsview-1/
  ext.config.yaml      Browse View extension point wiring
  web-src/src/components/
    ExtensionRegistration.js   registers the ActionBar button (+ quickActions)
    ContentHubModal.js         the modal form + confirm/POST logic
    Constants.js               extension id + status/target enums
  web-src/src/i18n/messages.js localized strings (en/fr/de/es)
aem-backend/           reference mirror of the AEM code (NOT deployed here)
BACKEND.md             backend architecture + HTTP contract
```

## Related

- [BACKEND.md](./BACKEND.md) — AEM backend contract and deployment
- [`aem-backend/README.md`](./aem-backend/README.md) — about the reference mirror

# Content Hub — AEM backend (required dependency)

This App Builder extension **does not work on its own**. The "Content Hub" button
in the Assets View only collects the user's choices and relays the request;
**all business logic lives in the AEM project** (servlet + Sling Job + OSGi
configurations). Both repositories must be deployed together.

## Two-repository architecture

```
┌─────────────────────────────┐        ┌──────────────────────────────────────┐
│  Extension (this GitHub repo)│        │  AEM backend (Cloud Manager git)       │
│                             │        │                                        │
│  ExtensionRegistration.js   │        │  ContentHubActionServlet.java          │
│   → ActionBar button        │        │   → POST .contenthub.json (202)        │
│  ContentHubModal.js         │  POST  │  ContentHubJobConsumer.java            │
│   → modal + fetch           │──────▶ │   → Sling Job: writes dam:status /     │
│  actions/contenthub/…       │ proxy  │     dam:activationTarget on the assets │
│   → Runtime proxy (CORS)    │        │  ui.config: service user, ACL, CORS    │
└─────────────────────────────┘        └──────────────────────────────────────┘
        adobeio-static.net                  author-pXXXXX-eXXXXXX.adobeaemcloud.com
```

**Call flow** (the browser never talks to AEM directly):

```
Browser ──POST (same-origin)──▶ actions/contenthub ──POST (server-to-server)──▶ AEM servlet
```

## Source of truth

The backend code is **maintained in the AEM project**, not here:

- Repo: `https://git.cloudmanager.adobe.com/adobedemoemea144/default/`
- Deployment: Adobe Cloud Manager — **Full Stack Pipeline**

The [`aem-backend/`](./aem-backend/) folder in this repo is a **reference mirror**
(for review purposes), not the deployed source. If the two diverge, the AEM repo
wins. See [`aem-backend/README.md`](./aem-backend/README.md).

## Required backend files

| File (path in the AEM repo) | Role |
|---|---|
| `core/src/main/java/com/dbounliane/core/servlets/ContentHubActionServlet.java` | POST endpoint `…/<folder>.contenthub.json`; validates input and enqueues the job (202). |
| `core/src/main/java/com/dbounliane/core/jobs/ContentHubJobConsumer.java` | Asynchronous Sling Job: writes `dam:status` + `dam:activationTarget` on `dam:Asset` nodes (optionally recursive). |
| `ui.config/…/config/…ServiceUserMapperImpl.amended~dbounliane-contenthub.cfg.json` | Maps the `contenthub` sub-service → system user `dbounliane-contenthub-service`. |
| `ui.config/…/config/…RepositoryInitializer~dbounliane.cfg.json` | repoinit: creates the system user + `jcr:read,rep:write` ACL on `/content/dam`. |
| `ui.config/…/config.author/…CORSPolicyImpl~dbounliane-contenthub.cfg.json` | CORS policy (see note below). |
| `core/src/test/java/com/dbounliane/core/**/*Test.java` | JUnit tests — required by the Cloud Manager quality gate. |

## HTTP contract

The extension calls the servlet through the `actions/contenthub` proxy:

- **Method / URL**: `POST /content/dam/<folder-path>.contenthub.json`
- **Auth**: `Authorization: Bearer <IMS token>` (relayed by the proxy)
- **Body** (`application/x-www-form-urlencoded`):

  | Parameter | Accepted values |
  |---|---|
  | `status` | `approved` \| `nostatus` → written to `dam:status` |
  | `activationTarget` | `delivery` \| `contenthub` → written to `dam:activationTarget` |
  | `recursive` | `true` \| `false` — include sub-folders |

- **Responses**:
  - `202 Accepted` → `{ "accepted": true, "jobId": "...", "path": "...", "status": "...", "activationTarget": "...", "recursive": bool }`
  - `400 Bad Request` → invalid or missing `status` / `activationTarget`
  - `500` → job could not be enqueued

> The servlet is bound to the `sling:Folder` / `sling:OrderedFolder` resource
> types with the `contenthub` selector (the recommended AEMaaCS approach — no
> `/bin` servlet path).

## Note on CORS

The `CORSPolicyImpl~dbounliane-contenthub` policy used to allow the direct
browser → AEM call. Since switching to the **same-origin Runtime proxy**
(`actions/contenthub`), the browser no longer calls AEM directly: the AEM call is
made server-to-server, **without CORS**. The policy is therefore now **optional**;
it is kept for a possible future direct usage and is not required for the
extension to work.

## Joint deployment

1. **AEM backend**: merge into the Cloud Manager repo → run the **Full Stack Pipeline**.
2. **Extension**: `aio app deploy`.

Until the backend is deployed, the proxy relays correctly but AEM responds with
`404` (the servlet does not exist yet).

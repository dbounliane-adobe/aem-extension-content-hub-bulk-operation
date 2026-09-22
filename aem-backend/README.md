# `aem-backend/` — reference mirror

⚠️ **This folder is NOT deployed by this extension.** It is a verbatim copy of the
backend code that lives in the AEM project and is deployed through Adobe Cloud
Manager.

- **Source of truth**: `https://git.cloudmanager.adobe.com/adobedemoemea144/default/`
- **Purpose here**: let reviewers read the backend contract (servlet, Sling Job,
  OSGi configs) without access to the Cloud Manager repo.
- **If the two diverge**, the AEM repo wins.

The directory structure mirrors the AEM repo, so each file maps directly to the
same location:

```
aem-backend/core/…        → core/…        (in the AEM repo)
aem-backend/ui.config/…    → ui.config/…   (in the AEM repo)
```

See [`../BACKEND.md`](../BACKEND.md) for the full architecture and HTTP contract.

## Keeping this mirror up to date

After any change to the backend in the AEM repo, resync the mirror:

```bash
# from the extension repo root, with AEM_REPO pointing at the AEM project
for f in \
  core/src/main/java/com/dbounliane/core/servlets/ContentHubActionServlet.java \
  core/src/main/java/com/dbounliane/core/jobs/ContentHubJobConsumer.java \
  core/src/test/java/com/dbounliane/core/servlets/ContentHubActionServletTest.java \
  core/src/test/java/com/dbounliane/core/jobs/ContentHubJobConsumerTest.java \
  ui.config/src/main/content/jcr_root/apps/dbounliane/osgiconfig/config.author/com.adobe.granite.cors.impl.CORSPolicyImpl~dbounliane-contenthub.cfg.json \
  ui.config/src/main/content/jcr_root/apps/dbounliane/osgiconfig/config/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~dbounliane-contenthub.cfg.json \
  ui.config/src/main/content/jcr_root/apps/dbounliane/osgiconfig/config/org.apache.sling.jcr.repoinit.RepositoryInitializer~dbounliane.cfg.json ; do
  mkdir -p "aem-backend/$(dirname "$f")" && cp "$AEM_REPO/$f" "aem-backend/$f"
done
```

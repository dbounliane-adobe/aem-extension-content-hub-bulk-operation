import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { attach } from "@adobe/uix-guest";
import { IntlProvider, useIntl } from "react-intl";
import {
  Provider,
  defaultTheme,
  Flex,
  Divider,
  Heading,
  Text,
  RadioGroup,
  Radio,
  Checkbox,
  ButtonGroup,
  Button,
  ProgressCircle,
} from "@adobe/react-spectrum";

import { extensionId, STATUS, ACTIVATION_TARGET } from "./Constants";
import messages, { resolveLocale, DEFAULT_LOCALE } from "../i18n/messages";

const LOG = "[ContentHub]";

// Fallback AEM author host, used when the guest sharedContext does not expose
// one (which is the case on this tenant). Override per environment if needed.
const DEFAULT_AEM_HOST = "https://author-p199354-e2062419.adobeaemcloud.com";

// Normalize a bare hostname or full URL to a clean https origin, or "".
function toOrigin(value) {
  if (!value || typeof value !== "string") return "";
  let v = value.trim();
  if (!v) return "";
  if (!/^https?:\/\//i.test(v)) v = `https://${v}`;
  try {
    const u = new URL(v);
    return `${u.protocol}//${u.host}`;
  } catch (_) {
    return "";
  }
}

// Resolve the current AEM author host from the guest connection. AEM Assets
// View has no single documented field, so probe several and log them.
function resolveAemHost(gc) {
  const sc = gc && gc.sharedContext;
  const candidates = [
    ["aemHost", sc && sc.get && sc.get("aemHost")],
    ["aemTierHost", sc && sc.get && sc.get("aemTierHost")],
    ["host", sc && sc.get && sc.get("host")],
    ["repositoryId", sc && sc.get && sc.get("repositoryId")],
    ["repo", sc && sc.get && sc.get("repo")],
  ];
  console.log(LOG, "resolveAemHost candidates:", candidates);
  for (const [, raw] of candidates) {
    const origin = toOrigin(raw);
    if (origin) return origin;
  }
  // Nothing in the shared context on this tenant — fall back to the known host.
  console.log(LOG, "resolveAemHost -> using DEFAULT_AEM_HOST fallback");
  return toOrigin(DEFAULT_AEM_HOST);
}

// AEM Assets View does NOT populate the IMS token in sharedContext; it must be
// fetched via the host auth RPC, with sharedContext fallbacks.
async function resolveIms(gc) {
  let imsToken;
  let imsOrg;
  try {
    const info = gc.host && gc.host.auth && gc.host.auth.getIMSInfo
      ? await gc.host.auth.getIMSInfo()
      : null;
    if (info) {
      imsToken = info.accessToken || info.imsToken || info.token;
      imsOrg = info.imsOrg || info.imsOrgId || info.orgId || info.ownerOrg;
    }
  } catch (e) {
    console.warn(LOG, "host.auth.getIMSInfo failed, trying sharedContext", e);
  }
  if (!imsToken && gc.sharedContext) {
    const auth = gc.sharedContext.get && gc.sharedContext.get("auth");
    if (auth) {
      imsToken = auth.imsToken;
      imsOrg = imsOrg || auth.imsOrg;
    }
    if (!imsToken && gc.sharedContext.get) imsToken = gc.sharedContext.get("imsToken");
  }
  return { imsToken, imsOrg };
}

/**
 * Modal form contributed by the Content Hub ActionBar button. Lets the user:
 *  - approve (dam:status=approved) or disapprove (dam:status=nostatus) the content,
 *  - choose the activation target (delivery = Dynamic Media & Content Hub, or
 *    contenthub = Content Hub only),
 *  - opt into recursive processing of sub-folders,
 * then posts the request to the AEM backend servlet, which enqueues an async job.
 */
function ContentHubForm({ folderPath, connection }) {
  const intl = useIntl();
  const [status, setStatus] = useState(STATUS.APPROVED);
  const [target, setTarget] = useState(ACTIVATION_TARGET.DELIVERY);
  const [recursive, setRecursive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(null); // { tone: 'positive'|'negative', text }

  const close = () => {
    // AEM Assets View uses closeDialog() (not close()).
    const modal = connection && connection.host && connection.host.modal;
    if (modal && modal.closeDialog) modal.closeDialog();
    else if (modal && modal.close) modal.close();
  };

  const confirm = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const { imsToken, imsOrg } = await resolveIms(connection);
      if (!imsToken) {
        throw new Error("Aucun token IMS obtenu (host.auth.getIMSInfo + sharedContext vides).");
      }
      if (!imsOrg) {
        throw new Error("Organisation IMS introuvable (x-gw-ims-org-id requis par require-adobe-auth).");
      }
      const aemHost = resolveAemHost(connection);
      if (!aemHost) {
        throw new Error("Host AEM introuvable dans le contexte (voir les 'resolveAemHost candidates' en console).");
      }

      // Go through the App Builder Runtime proxy action instead of calling AEM
      // directly: the browser -> AEM POST is blocked by CORS, but the action is
      // served from this very SPA's static host, so the call is same-origin (no
      // CORS, no preflight), and the action then reaches AEM server-to-server.
      const actionUrl = `${window.location.origin}/api/v1/web/dbounliane-contenthub-ext/contenthub`;
      console.log(LOG, "posting to Runtime action", { actionUrl, aemHost, folderPath });

      const res = await fetch(actionUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${imsToken}`,
          // Required by the action's require-adobe-auth gateway. Safe to send:
          // the call is same-origin, so no CORS preflight rejects it.
          "x-gw-ims-org-id": imsOrg,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          aemHost,
          path: folderPath,
          status,
          activationTarget: target,
          recursive,
        }),
      });

      if (!res.ok) {
        const body = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status} ${body}`);
      }

      setFeedback({ tone: "positive", text: intl.formatMessage({ id: "toast.success" }, { path: folderPath }) });
      // Give the user a moment to see the success, then close.
      setTimeout(close, 800);
    } catch (e) {
      console.error(LOG, "confirm failed", e);
      setFeedback({ tone: "negative", text: intl.formatMessage({ id: "toast.error" }, { error: e.message }) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Provider theme={defaultTheme} colorScheme="light">
      <Flex direction="column" gap="size-200" margin="size-300">
        <Text>
          {intl.formatMessage({ id: "modal.folder" })} : <strong>{folderPath}</strong>
        </Text>
        <Divider size="S" />

        <RadioGroup
          label={intl.formatMessage({ id: "status.legend" })}
          value={status}
          onChange={setStatus}
          isDisabled={busy}
        >
          <Radio value={STATUS.APPROVED}>{intl.formatMessage({ id: "status.approve" })}</Radio>
          <Radio value={STATUS.NOSTATUS}>{intl.formatMessage({ id: "status.disapprove" })}</Radio>
        </RadioGroup>

        <RadioGroup
          label={intl.formatMessage({ id: "target.legend" })}
          value={target}
          onChange={setTarget}
          isDisabled={busy}
        >
          <Radio value={ACTIVATION_TARGET.DELIVERY}>
            {intl.formatMessage({ id: "target.delivery" })}
          </Radio>
          <Radio value={ACTIVATION_TARGET.CONTENTHUB}>
            {intl.formatMessage({ id: "target.contenthub" })}
          </Radio>
        </RadioGroup>

        <Checkbox isSelected={recursive} onChange={setRecursive} isDisabled={busy}>
          {intl.formatMessage({ id: "options.recursive" })}
        </Checkbox>

        {feedback ? (
          <Text
            UNSAFE_style={{ color: feedback.tone === "positive" ? "#0a7c2f" : "#d31510" }}
          >
            {feedback.text}
          </Text>
        ) : null}

        <Divider size="S" />
        <ButtonGroup align="end">
          <Button variant="secondary" onPress={close} isDisabled={busy}>
            {intl.formatMessage({ id: "action.cancel" })}
          </Button>
          <Button variant="accent" onPress={confirm} isDisabled={busy}>
            {busy ? <ProgressCircle size="S" isIndeterminate aria-label="loading" /> : null}
            {intl.formatMessage({ id: "action.confirm" })}
          </Button>
        </ButtonGroup>
      </Flex>
    </Provider>
  );
}

function ContentHubModal() {
  const { folderPath: encodedPath } = useParams();
  const folderPath = decodeURIComponent(encodedPath || "");
  const [connection, setConnection] = useState(null);
  const [locale, setLocale] = useState(DEFAULT_LOCALE);

  useEffect(() => {
    (async () => {
      const c = await attach({ id: extensionId });
      setConnection(c);
      setLocale(resolveLocale(c.sharedContext && c.sharedContext.get("locale")));
    })().catch((e) => console.error("attach failed", e));
  }, []);

  if (!connection) {
    return (
      <Provider theme={defaultTheme} colorScheme="light">
        <Flex margin="size-400" justifyContent="center">
          <ProgressCircle isIndeterminate aria-label="loading" />
        </Flex>
      </Provider>
    );
  }

  return (
    <IntlProvider locale={locale} messages={messages[locale]} defaultLocale={DEFAULT_LOCALE}>
      <ContentHubForm folderPath={folderPath} connection={connection} />
    </IntlProvider>
  );
}

export default ContentHubModal;

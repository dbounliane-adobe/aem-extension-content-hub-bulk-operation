/*
 * ExtensionRegistration — Browse View (aem/assets/browse/1).
 *
 * Registers a custom ActionBar action ("Content Hub") shown when a single
 * FOLDER is selected. Modeled on the working "3D viewer" extension:
 *   - the Browse host requires BOTH `actionBar` AND `quickActions` to be
 *     implemented, otherwise the extension is not recognized and getActions is
 *     never called;
 *   - `actionBar` must expose getActions + getHiddenBuiltInActions +
 *     overrideBuiltInAction;
 *   - onClick reaches the live connection through the getConnection accessor.
 */
import { useEffect } from "react";
import { register } from "@adobe/uix-guest";

import { extensionId } from "./Constants";
import messages, { resolveLocale } from "../i18n/messages";

const LOG = "[ContentHub]";

// Base URL of this SPA (same App Builder app, different hash routes).
function appBase() {
  return `${window.location.origin}${window.location.pathname}`;
}

// Heuristic: a DAM folder's last path segment has no file extension.
function looksLikeFolder(path) {
  if (!path) return false;
  const last = path.split("/").pop() || "";
  return !last.includes(".");
}

function localizedLabel(getConnection) {
  try {
    const conn = getConnection();
    const locale = resolveLocale(conn && conn.sharedContext && conn.sharedContext.get("locale"));
    return messages[locale]["button.label"];
  } catch (e) {
    console.warn(LOG, "locale resolution failed, using default label", e);
    return "Content Hub";
  }
}

// Browse View methods: custom ActionBar action + neutral QuickActions. Both
// namespaces MUST be present for the Browse host to recognize the extension.
function browseMethods(getConnection) {
  return {
    actionBar: {
      async getActions(args) {
        // Log RAW args (never JSON.stringify — host proxies can throw).
        console.log(LOG, "actionBar.getActions() called — RAW args:", args);
        let resources = [];
        try {
          resources = (args && args.resourceSelection && args.resourceSelection.resources) || [];
        } catch (e) {
          console.error(LOG, "getActions: failed to read args", e);
        }
        const target = resources[0];
        console.log(LOG, "getActions parsed", { count: resources.length, first: target });

        // Only offer the action for a single folder selection.
        if (resources.length !== 1 || !looksLikeFolder(target && target.path)) {
          return [];
        }

        const label = localizedLabel(getConnection);
        return [
          {
            id: "content-hub-bulk-approval",
            label,
            // Same icon family as the built-in "Add to collection" action.
            icon: "Collection",
            onClick() {
              const folderPath = (target && target.path) || "";
              getConnection().host.modal.openDialog({
                title: label,
                type: "modal",
                contentUrl: `${appBase()}#/content-hub/${encodeURIComponent(folderPath)}`,
                payload: { path: folderPath },
              });
            },
          },
        ];
      },
      async getHiddenBuiltInActions() {
        return [];
      },
      async overrideBuiltInAction() {
        return false;
      },
    },
    quickActions: {
      async getHiddenBuiltInActions() {
        return [];
      },
      async overrideBuiltInAction() {
        return false;
      },
    },
  };
}

export default function ExtensionRegistration() {
  useEffect(() => {
    let connection;
    console.log(LOG, "ExtensionRegistration mounted", { href: window.location.href });

    (async () => {
      try {
        console.log(LOG, "calling register()…", { id: extensionId });
        connection = await register({
          id: extensionId,
          methods: browseMethods(() => connection),
        });
        console.log(LOG, "register() connected ✓", connection);
      } catch (err) {
        console.error(LOG, "register() FAILED", err);
      }
    })();

    return () => {
      if (connection && connection.unregister) {
        connection.unregister();
      }
    };
  }, []);

  // Nothing visible — this route only wires the extension into the host UI.
  return null;
}

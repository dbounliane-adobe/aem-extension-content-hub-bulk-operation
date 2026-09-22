/**
 * Unique id of this extension. Must stay stable across deployments so AEM can
 * identify the registered extension.
 */
export const extensionId = "dbounliane-content-hub";

/** Allowed dam:status values, kept in sync with the AEM backend servlet. */
export const STATUS = {
  APPROVED: "approved",
  NOSTATUS: "nostatus",
};

/** Allowed dam:activationTarget values, kept in sync with the AEM backend servlet. */
export const ACTIVATION_TARGET = {
  DELIVERY: "delivery",
  CONTENTHUB: "contenthub",
};

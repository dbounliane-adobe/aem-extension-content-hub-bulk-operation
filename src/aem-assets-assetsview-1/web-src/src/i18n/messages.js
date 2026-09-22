/**
 * Localized strings for the Content Hub extension.
 *
 * Add a new locale by adding an entry here; keys must match across locales.
 * The active locale is resolved from the AEM host shared context (see
 * ContentHubModal), falling back to English.
 */
const messages = {
  en: {
    "button.label": "Content Hub",
    "modal.title": "Content Hub",
    "modal.folder": "Folder",
    "status.legend": "Content status",
    "status.approve": "Approve content",
    "status.disapprove": "Disapprove content",
    "target.legend": "Publication target",
    "target.delivery": "Dynamic Media & Content Hub",
    "target.contenthub": "Content Hub only",
    "options.recursive": "Apply recursively to sub-folders",
    "action.cancel": "Cancel",
    "action.confirm": "Confirm",
    "toast.success": "Processing started for {path}.",
    "toast.error": "The operation failed: {error}",
  },
  fr: {
    "button.label": "Content Hub",
    "modal.title": "Content Hub",
    "modal.folder": "Dossier",
    "status.legend": "Statut du contenu",
    "status.approve": "Approuver le contenu",
    "status.disapprove": "Désapprouver le contenu",
    "target.legend": "Cible de publication",
    "target.delivery": "Dynamic Media & Content Hub",
    "target.contenthub": "Content Hub uniquement",
    "options.recursive": "Appliquer récursivement aux sous-dossiers",
    "action.cancel": "Annuler",
    "action.confirm": "Confirmer",
    "toast.success": "Traitement lancé pour {path}.",
    "toast.error": "L'opération a échoué : {error}",
  },
  de: {
    "button.label": "Content Hub",
    "modal.title": "Content Hub",
    "modal.folder": "Ordner",
    "status.legend": "Inhaltsstatus",
    "status.approve": "Inhalt genehmigen",
    "status.disapprove": "Genehmigung zurückziehen",
    "target.legend": "Veröffentlichungsziel",
    "target.delivery": "Dynamic Media & Content Hub",
    "target.contenthub": "Nur Content Hub",
    "options.recursive": "Rekursiv auf Unterordner anwenden",
    "action.cancel": "Abbrechen",
    "action.confirm": "Bestätigen",
    "toast.success": "Verarbeitung für {path} gestartet.",
    "toast.error": "Der Vorgang ist fehlgeschlagen: {error}",
  },
  es: {
    "button.label": "Content Hub",
    "modal.title": "Content Hub",
    "modal.folder": "Carpeta",
    "status.legend": "Estado del contenido",
    "status.approve": "Aprobar el contenido",
    "status.disapprove": "Desaprobar el contenido",
    "target.legend": "Destino de publicación",
    "target.delivery": "Dynamic Media & Content Hub",
    "target.contenthub": "Solo Content Hub",
    "options.recursive": "Aplicar de forma recursiva a las subcarpetas",
    "action.cancel": "Cancelar",
    "action.confirm": "Confirmar",
    "toast.success": "Procesamiento iniciado para {path}.",
    "toast.error": "La operación ha fallado: {error}",
  },
};

export const DEFAULT_LOCALE = "en";

/**
 * Resolves the best matching supported locale for a raw locale string such as
 * "fr-FR" or "de".
 */
export function resolveLocale(rawLocale) {
  if (!rawLocale) {
    return DEFAULT_LOCALE;
  }
  const lang = rawLocale.toLowerCase().split("-")[0];
  return messages[lang] ? lang : DEFAULT_LOCALE;
}

export default messages;

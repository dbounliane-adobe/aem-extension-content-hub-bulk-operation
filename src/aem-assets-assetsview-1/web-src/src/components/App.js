import React from "react";
import { Routes, Route } from "react-router-dom";
import { ErrorBoundary } from "react-error-boundary";

import ExtensionRegistration from "./ExtensionRegistration";
import ContentHubModal from "./ContentHubModal";

function fallback({ error }) {
  return (
    <div style={{ padding: 16 }}>
      <h1>Something went wrong</h1>
      <pre>{error.message}</pre>
    </div>
  );
}

function App() {
  return (
    <ErrorBoundary FallbackComponent={fallback}>
      <Routes>
        {/* Invisible iframe that registers the extension with the Browse View host. */}
        <Route index element={<ExtensionRegistration />} />
        <Route path="index.html" element={<ExtensionRegistration />} />
        {/* Dedicated registration route targeted by ext.config.yaml `impl`. */}
        <Route path="register/browse" element={<ExtensionRegistration />} />
        {/* Modal content rendered by host.modal.openDialog(contentUrl). */}
        <Route path="content-hub/:folderPath" element={<ContentHubModal />} />
      </Routes>
    </ErrorBoundary>
  );
}

export default App;

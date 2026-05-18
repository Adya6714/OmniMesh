import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// Suppress the harmless "ResizeObserver loop completed with undelivered
// notifications" warning that webpack-dev-server's error overlay otherwise
// promotes to a fullscreen runtime error. The canvas-based macro map and
// other ResizeObserver consumers sometimes trigger it across browsers.
// See: https://github.com/WICG/resize-observer/issues/38
const RO_BENIGN = "ResizeObserver loop";
window.addEventListener("error", (event) => {
  if (event?.message?.includes(RO_BENIGN)) {
    event.stopImmediatePropagation();
    event.preventDefault();
  }
});
window.addEventListener("unhandledrejection", (event) => {
  if (typeof event?.reason?.message === "string" && event.reason.message.includes(RO_BENIGN)) {
    event.stopImmediatePropagation();
    event.preventDefault();
  }
});

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

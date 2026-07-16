import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { registerSW } from "virtual:pwa-register";
import App from "./App";
import { publishPwaUpdate } from "./lib/pwa-update";
import "./styles.css";

const updateSW = registerSW({
  onNeedRefresh() {
    publishPwaUpdate(updateSW);
  },
  onRegisteredSW(_url, registration) {
    window.setInterval(() => { void registration?.update(); }, 60 * 60 * 1000);
  }
});

document.documentElement.dataset.appVersion = __APP_VERSION__;

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode><BrowserRouter><App /></BrowserRouter></React.StrictMode>
);

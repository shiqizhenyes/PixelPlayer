import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App";
import ControlCenter from "./components/ControlCenter";

const urlParams = new URLSearchParams(window.location.search);
const view = urlParams.get("view");

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {view === "control" ? <ControlCenter /> : <App />}
  </StrictMode>,
);

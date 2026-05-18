import { GoogleGenerativeAI } from "@google/generative-ai";

/**
 * Default model ID for the Generative Language API (SDK routes to the same backend as REST).
 * Bare `gemini-1.5-flash` often 404s on current API versions — use `-latest` or override via env.
 */
const DEFAULT_MODEL = "gemini-2.5-flash";

const GEMINI_TIMEOUT_MS = 30_000;

let initDiagnosticsLogged = false;

/** Prefer REACT_APP_GEMINI_API_KEY; accept legacy REACT_APP_GEMINI_KEY so .env mismatches don't fail silently. */
function getResolvedApiKey() {
  const primary = (process.env.REACT_APP_GEMINI_API_KEY || "").trim();
  const legacy = (process.env.REACT_APP_GEMINI_KEY || "").trim();
  if (primary) return primary;
  if (legacy) return legacy;
  return "";
}

const getModelId = () => (process.env.REACT_APP_GEMINI_MODEL || DEFAULT_MODEL).trim();

function logGeminiInitOnce() {
  if (initDiagnosticsLogged) return;
  initDiagnosticsLogged = true;

  const key = getResolvedApiKey();
  const model = getModelId();

  console.log("[OmniMesh Gemini] Model:", model);
  console.log(
    "[OmniMesh Gemini] Using @google/generative-ai SDK (same API as generativelanguage.googleapis.com v1beta).",
  );

  if (!key) {
    console.error(
      "[OmniMesh Gemini] No API key loaded. Set REACT_APP_GEMINI_API_KEY in web/.env and restart dev server.",
    );
    if (process.env.REACT_APP_GEMINI_KEY && !process.env.REACT_APP_GEMINI_API_KEY) {
      console.error(
        '[OmniMesh Gemini] Found REACT_APP_GEMINI_KEY but code prefers REACT_APP_GEMINI_API_KEY — rename in .env or duplicate the variable.',
      );
    }
    return;
  }

  console.log("[OmniMesh Gemini] API key prefix (first 8 chars):", `${key.slice(0, 8)}***`);

  if ((process.env.REACT_APP_GEMINI_KEY || "").trim() && !(process.env.REACT_APP_GEMINI_API_KEY || "").trim()) {
    console.warn(
      "[OmniMesh Gemini] Using REACT_APP_GEMINI_KEY only — prefer REACT_APP_GEMINI_API_KEY for consistency.",
    );
  }
}

const extractJson = (text) => {
  const trimmed = text.trim();
  if (trimmed.startsWith("{")) {
    return trimmed;
  }

  const fencedMatch = trimmed.match(/```json([\s\S]*?)```/i);
  if (fencedMatch?.[1]) {
    return fencedMatch[1].trim();
  }

  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start !== -1 && end !== -1 && end > start) {
    return trimmed.slice(start, end + 1);
  }

  throw new Error("Agent response did not contain valid JSON.");
};

/**
 * Deterministic dispatch-shaped object when Gemini is unavailable or returns errors.
 * Keeps Command / Field analysis UI populated.
 */
export function buildDispatchFallback(packets) {
  const list = Array.isArray(packets) ? packets : [];
  const red = list.filter((p) => p.urgency === "RED");
  const yellow = list.filter((p) => p.urgency === "YELLOW");
  const green = list.filter((p) => p.urgency === "GREEN");

  const priority_order = [...list]
    .sort((a, b) => {
      const pr = { RED: 0, YELLOW: 1, GREEN: 2 };
      const u = (pr[a.urgency] ?? 3) - (pr[b.urgency] ?? 3);
      if (u !== 0) return u;
      return (Number(b.createdAt) || 0) - (Number(a.createdAt) || 0);
    })
    .map((p) => p.id);

  const zone_assignments = [];
  if (red.length) {
    zone_assignments.push({
      zone: "Sector Alpha · RED cluster",
      team: "ALS strike + incident command relay",
      reason: `${red.length} critical — airway, hemorrhage control, and extraction first.`,
    });
  }
  if (yellow.length) {
    zone_assignments.push({
      zone: "Sector Bravo · urgent care",
      team: "Secondary EMS + staging",
      reason: `${yellow.length} urgent — fractures, burns, and respiratory support.`,
    });
  }
  if (green.length) {
    zone_assignments.push({
      zone: "Sector Charlie · walking wounded",
      team: "Volunteer medics + self-evac corridor",
      reason: `${green.length} stable — routing and hydration.`,
    });
  }

  let critical_alert = null;
  if (red.length >= 2) {
    critical_alert = `${red.length} RED casualties — consolidate nearest ALS and structural clearance before entry.`;
  } else if (red.length === 1) {
    critical_alert = "Single RED casualty — dispatch closest paramedic unit and reserve hoist/extraction.";
  } else if (yellow.length >= 4 && red.length === 0) {
    critical_alert = "High yellow load — open parallel staging lanes.";
  }

  return {
    priority_order,
    zone_assignments,
    critical_alert,
    estimated_casualties: list.length,
    analysis:
      `Tactical fallback (AI route unavailable): ${list.length} packets — ${red.length} RED, ${yellow.length} YELLOW, ${green.length} GREEN. ` +
      `Execute Alpha → Bravo → Charlie by severity; confirm mesh handoffs at relays.`,
  };
}

function normalizeAnalysis(raw, packets) {
  const fb = buildDispatchFallback(packets);
  if (!raw || typeof raw !== "object") return fb;

  const zones = Array.isArray(raw.zone_assignments)
    ? raw.zone_assignments.map((z) => ({
        zone: z.zone || "Zone",
        team: z.team ?? z.resources ?? "Assign team",
        reason: z.reason ?? z.reasoning ?? "",
      }))
    : fb.zone_assignments;

  return {
    priority_order: Array.isArray(raw.priority_order) ? raw.priority_order : fb.priority_order,
    zone_assignments: zones.length ? zones : fb.zone_assignments,
    critical_alert: raw.critical_alert ?? fb.critical_alert,
    estimated_casualties:
      raw.estimated_casualties != null ? raw.estimated_casualties : fb.estimated_casualties,
    analysis: typeof raw.analysis === "string" && raw.analysis.trim() ? raw.analysis.trim() : fb.analysis,
  };
}

function withTimeout(promise, ms, label) {
  let timerId;
  const timeoutPromise = new Promise((_, reject) => {
    timerId = setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms);
  });
  return Promise.race([
    promise.finally(() => {
      clearTimeout(timerId);
    }),
    timeoutPromise,
  ]);
}

export async function runDispatchAgent(packets) {
  logGeminiInitOnce();

  const apiKey = getResolvedApiKey();
  if (!apiKey) {
    const err = new Error("Missing REACT_APP_GEMINI_API_KEY (or legacy REACT_APP_GEMINI_KEY) in environment.");
    console.error("Gemini dispatch error:", err);
    return {
      ...buildDispatchFallback(packets),
      critical_alert: "Gemini API key not configured",
      analysis:
        `${buildDispatchFallback(packets).analysis} Configure REACT_APP_GEMINI_API_KEY in web/.env and restart the dev server.`,
    };
  }

  const prompt = `You are OmniMesh Dispatch Agent operating in a disaster triage center.
Output RULES: Respond with a single JSON object only. No markdown, no code fences, no prose outside JSON.

Required JSON shape:
- priority_order: string[] — packet IDs, highest urgency first
- zone_assignments: { zone: string, team: string, reason: string }[] — use Alpha, Bravo, Charlie sector naming where appropriate
- critical_alert: string | null
- estimated_casualties: number
- analysis: string — concise operational paragraph for incident command

Triage packets:
${JSON.stringify(packets, null, 2)}`;

  const modelId = getModelId();

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
      model: modelId,
      generationConfig: {
        temperature: 0,
        maxOutputTokens: 800,
        responseMimeType: "application/json",
      },
    });

    const result = await withTimeout(model.generateContent(prompt), GEMINI_TIMEOUT_MS, "Gemini generateContent");

    let text;
    try {
      text = result.response.text();
    } catch (responseErr) {
      console.error("Gemini dispatch error: failed to read response.text()", responseErr);
      throw responseErr;
    }

    if (!text?.trim()) {
      const emptyErr = new Error("Gemini response missing text content.");
      console.error("Gemini dispatch error:", emptyErr);
      throw emptyErr;
    }

    let jsonSlice;
    try {
      jsonSlice = extractJson(text);
    } catch (extractErr) {
      console.error("Gemini dispatch error: JSON extract failed", extractErr);
      console.error("Gemini raw response snippet:", text.slice(0, 500));
      throw extractErr;
    }

    let parsed;
    try {
      parsed = JSON.parse(jsonSlice);
    } catch (parseErr) {
      console.error("Gemini dispatch error: JSON.parse failed", parseErr);
      console.error("Gemini extracted slice:", jsonSlice.slice(0, 800));
      throw parseErr;
    }

    return normalizeAnalysis(parsed, packets);
  } catch (error) {
    console.error("Gemini dispatch error:", error);
    console.error("Gemini dispatch error (message):", error?.message);
    console.error("Gemini dispatch error (stack):", error?.stack);
    if (error?.response?.data !== undefined) {
      console.error("Gemini response:", error.response.data);
    }
    if (error?.cause) {
      console.error("Gemini dispatch error (cause):", error.cause);
    }
    console.warn("[OmniMesh Gemini] Returning tactical fallback analysis.");
    return buildDispatchFallback(packets);
  }
}

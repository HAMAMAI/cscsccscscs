import http from "node:http";
import { randomUUID } from "node:crypto";
import { AccessToken, TrackSource } from "livekit-server-sdk";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PORT = Number(process.env.PORT || 7890);
const required = [
  "SUPABASE_URL",
  "SUPABASE_PUBLISHABLE_KEY",
  "LIVEKIT_API_KEY",
  "LIVEKIT_API_SECRET",
  "LIVEKIT_WS_URL",
];

for (const name of required) {
  if (!process.env[name]) throw new Error(`Missing ${name}`);
}

const buckets = new Map();
function allowed(address) {
  const now = Date.now();
  const current = buckets.get(address);
  if (!current || now - current.startedAt > 60_000) {
    buckets.set(address, { startedAt: now, count: 1 });
    return true;
  }
  current.count += 1;
  return current.count <= 30;
}

function reply(response, status, body) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store, max-age=0",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(JSON.stringify(body));
}

function bearerToken(header) {
  const match = /^Bearer\s+(.+)$/i.exec(String(header || ""));
  return match?.[1]?.trim() || null;
}

/**
 * A LiveKit identity must be unique per connected participant. The authenticated
 * user ID remains the authority prefix, while this per-installation ID lets one
 * account join the same room from a phone and a PC at the same time.
 */
function participantDeviceId(header) {
  const value = String(header || "").trim();
  return UUID.test(value) ? value : randomUUID();
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 16_384) throw new Error("Request too large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function callContext(accessToken, callId) {
  const validation = await fetch(`${process.env.SUPABASE_URL}/rest/v1/rpc/takt_call_token_context`, {
    method: "POST",
    headers: {
      apikey: process.env.SUPABASE_PUBLISHABLE_KEY,
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_call_id: callId }),
  });
  if (!validation.ok) return null;
  const payload = await validation.json();
  const value = Array.isArray(payload) ? payload[0] : payload;
  if (!value || !UUID.test(value.identity || "") || typeof value.room_name !== "string") return null;
  return value;
}

const server = http.createServer(async (request, response) => {
  const path = new URL(request.url || "/", "http://127.0.0.1").pathname;
  if (request.method === "GET" && path === "/health") return reply(response, 200, { ok: true });
  if (request.method !== "POST" || path !== "/api/livekit-token") return reply(response, 404, { error: "Not found" });
  if (!allowed(request.socket.remoteAddress || "unknown")) return reply(response, 429, { error: "Too many requests" });

  try {
    const accessToken = bearerToken(request.headers.authorization);
    if (!accessToken) return reply(response, 401, { error: "Authentication required" });
    const input = await readJson(request);
    if (!UUID.test(input.callId || "")) return reply(response, 400, { error: "Invalid call id" });

    // RLS and the authenticated Supabase JWT establish membership and call state.
    // A client never sends an identity, room name, or LiveKit secret.
    const context = await callContext(accessToken, input.callId);
    if (!context) return reply(response, 403, { error: "Access denied" });

    // Screen sharing belongs in voice calls too. A client can only obtain this
    // token after RLS has verified it belongs to the chat/call session.
    const sources = [
      TrackSource.MICROPHONE,
      TrackSource.SCREEN_SHARE,
      TrackSource.SCREEN_SHARE_AUDIO,
    ];
    if (context.is_video === true) sources.push(TrackSource.CAMERA);
    const token = new AccessToken(process.env.LIVEKIT_API_KEY, process.env.LIVEKIT_API_SECRET, {
      identity: `${context.identity}:${participantDeviceId(request.headers["x-takt-device-id"])}`,
      name: String(context.display_name || "Takt user").slice(0, 128),
      ttl: "10m",
    });
    token.addGrant({
      room: context.room_name,
      roomJoin: true,
      canPublish: true,
      canSubscribe: true,
      canPublishData: false,
      canPublishSources: sources,
    });
    return reply(response, 200, { serverUrl: process.env.LIVEKIT_WS_URL, token: await token.toJwt() });
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    return reply(response, 400, { error: "Invalid request" });
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Token service listening on 127.0.0.1:${PORT}`);
});

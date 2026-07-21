import http from "node:http";
import { AccessToken, TrackSource } from "livekit-server-sdk";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TOKEN = /^[0-9a-f]{64}$/i;
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

const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    return reply(response, 200, { ok: true });
  }
  if (request.method !== "POST" || request.url !== "/api/livekit-token") {
    return reply(response, 404, { error: "Not found" });
  }
  if (!allowed(request.socket.remoteAddress || "unknown")) {
    return reply(response, 429, { error: "Too many requests" });
  }

  try {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
      size += chunk.length;
      if (size > 16_384) throw new Error("Request too large");
      chunks.push(chunk);
    }
    const input = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!UUID.test(input.participantId || "") || !UUID.test(input.roomId || "") || !TOKEN.test(input.participantToken || "")) {
      return reply(response, 400, { error: "Invalid call session" });
    }

    const validation = await fetch(`${process.env.SUPABASE_URL}/rest/v1/rpc/validate_call_session`, {
      method: "POST",
      headers: {
        apikey: process.env.SUPABASE_PUBLISHABLE_KEY,
        Authorization: `Bearer ${process.env.SUPABASE_PUBLISHABLE_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        p_room_id: input.roomId,
        p_participant_id: input.participantId,
        p_token: input.participantToken,
      }),
    });
    if (!validation.ok) return reply(response, 401, { error: "Access denied" });

    const participant = await validation.json();
    const accessToken = new AccessToken(process.env.LIVEKIT_API_KEY, process.env.LIVEKIT_API_SECRET, {
      identity: participant.participant_id,
      name: participant.display_name,
      ttl: "10m",
    });
    accessToken.addGrant({
      room: `takt-${participant.room_id}`,
      roomJoin: true,
      canPublish: true,
      canSubscribe: true,
      canPublishData: false,
      canPublishSources: [TrackSource.MICROPHONE],
    });
    return reply(response, 200, {
      serverUrl: process.env.LIVEKIT_WS_URL,
      token: await accessToken.toJwt(),
    });
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    return reply(response, 400, { error: "Invalid request" });
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Token service listening on 127.0.0.1:${PORT}`);
});

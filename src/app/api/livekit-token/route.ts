import { AccessToken, TrackSource } from "livekit-server-sdk";
import { NextRequest, NextResponse } from "next/server";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TOKEN = /^[0-9a-f]{64}$/i;

export async function POST(request: NextRequest) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;
  const serverUrl = process.env.LIVEKIT_WS_URL;

  if (!supabaseUrl || !supabaseKey || !apiKey || !apiSecret || !serverUrl) {
    return NextResponse.json({ error: "Calls are not configured" }, { status: 503 });
  }

  let input: { participantId?: string; participantToken?: string; roomId?: string };
  try {
    input = await request.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON" }, { status: 400 });
  }

  if (!input.participantId?.match(UUID) || !input.roomId?.match(UUID) || !input.participantToken?.match(TOKEN)) {
    return NextResponse.json({ error: "Invalid call session" }, { status: 400 });
  }

  const validation = await fetch(`${supabaseUrl}/rest/v1/rpc/validate_call_session`, {
    method: "POST",
    headers: {
      apikey: supabaseKey,
      Authorization: `Bearer ${supabaseKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      p_room_id: input.roomId,
      p_participant_id: input.participantId,
      p_token: input.participantToken,
    }),
    cache: "no-store",
  });

  if (!validation.ok) {
    return NextResponse.json({ error: "Access denied" }, { status: 401 });
  }

  const participant = await validation.json() as {
    room_id: string;
    participant_id: string;
    display_name: string;
  };
  const roomName = `takt-${participant.room_id}`;
  const accessToken = new AccessToken(apiKey, apiSecret, {
    identity: participant.participant_id,
    name: participant.display_name,
    ttl: "10m",
  });
  accessToken.addGrant({
    room: roomName,
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
    canPublishData: false,
    canPublishSources: [TrackSource.MICROPHONE],
  });

  return NextResponse.json(
    { serverUrl, token: await accessToken.toJwt() },
    { headers: { "Cache-Control": "no-store, max-age=0" } },
  );
}

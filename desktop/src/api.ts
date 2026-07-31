import { createClient } from "@supabase/supabase-js";
import type {
  ActiveCall,
  BootstrapData,
  CallCredentials,
  ChatState,
  ConversationSummary,
  MessengerMessage,
  UserProfile,
} from "./types";

const SUPABASE_URL =
  import.meta.env.VITE_TAKT_SUPABASE_URL ?? "https://qewgunjxdpliyeazyjkn.supabase.co";
const SUPABASE_PUBLISHABLE_KEY =
  import.meta.env.VITE_TAKT_SUPABASE_PUBLISHABLE_KEY ??
  "sb_publishable_oFE1KGP-BLnRJT_IaJ30Bg_eaFyPaf2";
const CALL_TOKEN_URL =
  import.meta.env.VITE_TAKT_CALL_TOKEN_URL ?? "https://call.195-123-6-26.sslip.io";

export const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: false,
    storageKey: "takt-desktop-auth",
  },
});

function unwrap<T>(value: T | T[] | null): T {
  if (Array.isArray(value)) {
    if (!value[0]) throw new Error("Сервер не вернул данные");
    return value[0];
  }
  if (!value) throw new Error("Сервер не вернул данные");
  return value;
}

async function rpc<T>(name: string, args: Record<string, unknown> = {}): Promise<T> {
  const { data, error } = await supabase.rpc(name, args);
  if (error) throw new Error(error.message);
  return data as T;
}

export async function bootstrap(): Promise<BootstrapData> {
  return unwrap<BootstrapData>(await rpc<BootstrapData | BootstrapData[]>("takt_bootstrap"));
}

export async function getConversation(conversationId: string): Promise<ChatState> {
  return unwrap<ChatState>(
    await rpc<ChatState | ChatState[]>("takt_get_conversation", {
      p_conversation_id: conversationId,
    }),
  );
}

export async function searchPeople(query: string): Promise<UserProfile[]> {
  const data = await rpc<UserProfile[] | { data?: UserProfile[] }>("takt_search_people", {
    p_query: query,
  });
  return Array.isArray(data) ? data : data.data ?? [];
}

export async function openDirectChat(userId: string): Promise<ChatState> {
  return unwrap<ChatState>(
    await rpc<ChatState | ChatState[]>("takt_open_direct_chat", {
      p_other_user_id: userId,
    }),
  );
}

export async function sendText(
  conversationId: string,
  body: string,
  replyToId: string | null = null,
): Promise<MessengerMessage> {
  return unwrap<MessengerMessage>(
    await rpc<MessengerMessage | MessengerMessage[]>("takt_send_message", {
      p_conversation_id: conversationId,
      p_body: body,
      p_reply_to_id: replyToId,
      p_forwarded_from_id: null,
    }),
  );
}

function toBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Не удалось прочитать файл"));
    reader.onload = () => {
      const result = reader.result;
      if (typeof result !== "string") {
        reject(new Error("Не удалось прочитать файл"));
        return;
      }
      resolve(result.slice(result.indexOf(",") + 1));
    };
    reader.readAsDataURL(file);
  });
}

function attachmentKind(file: File): string {
  if (file.type.startsWith("image/")) return "image";
  if (file.type.startsWith("video/")) return "video";
  if (file.type.startsWith("audio/")) return "voice";
  return "file";
}

export async function sendAttachment(
  conversationId: string,
  file: File,
): Promise<MessengerMessage> {
  const bytes = await toBase64(file);
  return unwrap<MessengerMessage>(
    await rpc<MessengerMessage | MessengerMessage[]>("takt_send_attachment", {
      p_conversation_id: conversationId,
      p_file_name: file.name.slice(0, 255),
      p_mime_type: file.type || "application/octet-stream",
      p_base64: bytes,
      p_kind: attachmentKind(file),
      p_duration_seconds: null,
      p_caption: "",
    }),
  );
}

export async function markRead(conversationId: string): Promise<void> {
  await rpc("takt_mark_read", { p_conversation_id: conversationId });
}

export async function toggleReaction(messageId: string, emoji: string): Promise<MessengerMessage> {
  return unwrap<MessengerMessage>(
    await rpc<MessengerMessage | MessengerMessage[]>("takt_toggle_reaction", {
      p_message_id: messageId,
      p_emoji: emoji,
    }),
  );
}

export async function setTyping(conversationId: string, mode: "typing" | "idle"): Promise<void> {
  await rpc("takt_set_typing", { p_conversation_id: conversationId, p_mode: mode });
}

export async function setPresence(online: boolean): Promise<void> {
  await rpc("takt_set_presence", { p_online: online });
}

export async function updateChatSettings(
  conversation: ConversationSummary,
  change: { archived?: boolean; mutedUntil?: string | null },
): Promise<ChatState> {
  return unwrap<ChatState>(
    await rpc<ChatState | ChatState[]>("takt_update_chat_settings", {
      p_conversation_id: conversation.id,
      p_is_archived: change.archived ?? null,
      p_is_pinned: null,
      p_folder_id: null,
      p_muted_until: change.mutedUntil ?? null,
      p_draft_text: null,
    }),
  );
}

export async function startOrJoinCall(conversationId: string, video = false): Promise<ActiveCall> {
  return unwrap<ActiveCall>(
    await rpc<ActiveCall | ActiveCall[]>("takt_start_call", {
      p_conversation_id: conversationId,
      p_is_video: video,
    }),
  );
}

export async function endCall(callId: string): Promise<void> {
  await rpc("takt_end_call", { p_call_id: callId });
}

function getDeviceId(): string {
  const storageKey = "takt-desktop-device-id";
  const existing = window.localStorage.getItem(storageKey);
  if (existing) return existing;
  const value = crypto.randomUUID();
  window.localStorage.setItem(storageKey, value);
  return value;
}

export async function getCallCredentials(callId: string): Promise<CallCredentials> {
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session?.access_token) throw new Error("Сессия входа истекла. Войдите снова.");

  const endpoint = CALL_TOKEN_URL.replace(/\/$/, "");
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 15_000);
  let response: Response;
  try {
    response = await fetch(endpoint + "/api/livekit-token", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + session.access_token,
        "Content-Type": "application/json",
        "X-Takt-Device-Id": getDeviceId(),
      },
      body: JSON.stringify({ callId }),
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error("Сервер звонков не ответил за 15 секунд. Попробуйте ещё раз.");
    }
    throw new Error("Не удалось связаться с сервером звонков.");
  } finally {
    window.clearTimeout(timeout);
  }
  const payload = (await response.json().catch(() => ({}))) as Partial<CallCredentials & { error: string }>;
  if (!response.ok || !payload.serverUrl || !payload.token) {
    throw new Error(payload.error ?? "Не удалось получить доступ к звонку");
  }
  return { serverUrl: payload.serverUrl, token: payload.token };
}

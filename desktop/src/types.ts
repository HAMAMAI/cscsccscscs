export type UserProfile = {
  id: string;
  email?: string;
  username?: string | null;
  display_name: string;
  about?: string;
  avatar_color?: string;
  last_seen_at?: string | null;
  is_online?: boolean;
};

export type ChatSettings = {
  is_archived?: boolean;
  is_pinned?: boolean;
  folder_id?: string | null;
  muted_until?: string | null;
  draft_text?: string;
};

export type Attachment = {
  id: string;
  file_name: string;
  mime_type: string;
  size_bytes: number;
  duration_seconds?: number | null;
};

export type Reaction = {
  emoji: string;
  count: number;
  mine: boolean;
};

export type ReplyPreview = {
  id: string;
  body: string;
  kind: string;
  sender_name: string;
};

export type MessengerMessage = {
  id: string;
  conversation_id: string;
  sender_id: string;
  sender_name: string;
  sender_color?: string;
  body: string;
  kind: "text" | "image" | "video" | "file" | "voice" | string;
  created_at: string;
  edited_at?: string | null;
  deleted_at?: string | null;
  is_pinned?: boolean;
  reply_to_id?: string | null;
  reply_preview?: ReplyPreview | null;
  forwarded_from_id?: string | null;
  attachment?: Attachment | null;
  reactions?: Reaction[];
  status?: "sending" | "sent" | "delivered" | "read" | "received" | "deleted" | string;
};

export type ChatMember = {
  id: string;
  display_name: string;
  username?: string | null;
  avatar_color?: string;
  role: string;
  is_online?: boolean;
  last_seen_at?: string | null;
};

export type TypingState = {
  user_id: string;
  display_name: string;
  mode: string;
};

export type ActiveCall = {
  id: string;
  conversation_id: string;
  started_by: string;
  started_by_name?: string;
  is_video: boolean;
  started_at: string;
};

export type ConversationSummary = {
  id: string;
  kind: "direct" | "group" | string;
  title: string;
  avatar_color?: string;
  is_archived?: boolean;
  is_pinned?: boolean;
  folder_id?: string | null;
  muted_until?: string | null;
  draft_text?: string;
  updated_at: string;
  last_message?: MessengerMessage | null;
  unread_count?: number;
};

export type ChatState = {
  id: string;
  kind: "direct" | "group" | string;
  title: string;
  avatar_color?: string;
  created_at: string;
  settings?: ChatSettings;
  members: ChatMember[];
  messages: MessengerMessage[];
  typing: TypingState[];
  active_call?: ActiveCall | null;
};

export type ChatFolder = {
  id: string;
  name: string;
  color: string;
  position: number;
};

export type CallHistoryItem = {
  id: string;
  conversation_id: string;
  is_video: boolean;
  started_by: string;
  started_at: string;
  ended_at?: string | null;
};

export type BootstrapData = {
  profile: UserProfile;
  conversations: ConversationSummary[];
  folders: ChatFolder[];
  calls: CallHistoryItem[];
};

export type CallCredentials = {
  serverUrl: string;
  token: string;
};

export type CallUiState = {
  connected: boolean;
  muted: boolean;
  cameraEnabled: boolean;
  screenSharing: boolean;
  participantCount: number;
};

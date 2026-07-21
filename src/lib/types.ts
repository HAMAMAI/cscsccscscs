export type Participant = {
  id: string;
  display_name: string;
  color: string;
  is_owner: boolean;
};

export type Message = {
  id: string;
  room_id: string;
  participant_id: string;
  display_name: string;
  color: string;
  body: string;
  kind: "text" | "image" | "file" | "audio";
  attachment_id: string | null;
  attachment: Attachment | null;
  created_at: string;
};

export type Attachment = {
  id: string;
  file_name: string;
  mime_type: string;
  size_bytes: number;
  duration_seconds: number | null;
};

export type Session = {
  roomId: string;
  inviteCode: string;
  participantId: string;
  participantToken: string;
  displayName: string;
  roomName: string;
  color: string;
};

export type RoomState = {
  room: { id: string; name: string; invite_code: string; created_at: string };
  me: Participant;
  participants: Participant[];
  messages: Message[];
};

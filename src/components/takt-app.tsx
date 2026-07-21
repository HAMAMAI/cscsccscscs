"use client";

import {
  ArrowLeft,
  AudioLines,
  Check,
  ChevronRight,
  Copy,
  Download,
  FileText,
  Image as ImageIcon,
  Link2,
  Menu,
  MessageCircleMore,
  Mic,
  MicOff,
  Moon,
  MoreHorizontal,
  Paperclip,
  Phone,
  PhoneCall,
  PhoneOff,
  Plus,
  Search,
  Send,
  Share2,
  Smile,
  Sparkles,
  Square,
  Sun,
  Users,
  X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { RealtimeChannel } from "@supabase/supabase-js";
import { supabase } from "@/lib/supabase";
import type { Attachment, Message, Participant, RoomState, Session } from "@/lib/types";

const SESSION_KEY = "takt-session-v1";
const EMOJIS = ["🙂", "😂", "❤️", "🔥", "👏", "✨", "👍", "🎧", "☕", "🚀"];
const MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;
const BLOCKED_MIME_TYPES = new Set(["text/html", "image/svg+xml", "application/javascript", "text/javascript", "application/x-sh"]);

type RpcSession = {
  room_id: string;
  invite_code: string;
  participant_id: string;
  participant_token: string;
  display_name: string;
  room_name: string;
  color: string;
};

type Signal = {
  kind: "call-invite" | "call-answer" | "ice-candidate" | "call-decline" | "call-end";
  from: string;
  to?: string;
  name?: string;
  offer?: RTCSessionDescriptionInit;
  answer?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
};

type IncomingCall = { callerId: string; callerName: string; offer: RTCSessionDescriptionInit };

function cleanName(value: string) {
  return value.trim().replace(/\s+/g, " ").slice(0, 32);
}

function toSession(row: RpcSession): Session {
  return {
    roomId: row.room_id,
    inviteCode: row.invite_code,
    participantId: row.participant_id,
    participantToken: row.participant_token,
    displayName: row.display_name,
    roomName: row.room_name,
    color: row.color,
  };
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("ru", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} Б`;
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} КБ`;
  return `${(value / 1024 / 1024).toFixed(value < 10 * 1024 * 1024 ? 1 : 0)} МБ`;
}

function formatDuration(value: number) {
  const minutes = Math.floor(value / 60);
  return `${minutes}:${String(value % 60).padStart(2, "0")}`;
}

function fileToBase64(file: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error("Не удалось прочитать файл"));
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
    reader.readAsDataURL(file);
  });
}

function base64ToObjectUrl(base64: string, mimeType: string) {
  const binary = atob(base64.replace(/\s/g, ""));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return URL.createObjectURL(new Blob([bytes], { type: mimeType }));
}

function initials(name: string) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function TaktApp({ inviteCode }: { inviteCode?: string }) {
  const [session, setSession] = useState<Session | null>(null);
  const [booted, setBooted] = useState(false);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(SESSION_KEY);
      if (saved) {
        const parsed = JSON.parse(saved) as Session;
        if (!inviteCode || parsed.inviteCode === inviteCode) setSession(parsed);
      }
    } catch {
      localStorage.removeItem(SESSION_KEY);
    } finally {
      setBooted(true);
    }
  }, [inviteCode]);

  const saveSession = (next: Session) => {
    localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    setSession(next);
  };

  if (!booted) {
    return (
      <main className="splash">
        <Logo />
        <div className="pulse-loader"><i /><i /><i /></div>
      </main>
    );
  }

  return session ? (
    <Messenger session={session} onLeave={() => {
      localStorage.removeItem(SESSION_KEY);
      setSession(null);
      window.history.replaceState({}, "", "/");
    }} />
  ) : (
    <Onboarding inviteCode={inviteCode} onReady={saveSession} />
  );
}

function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`logo ${compact ? "logo-compact" : ""}`} aria-label="Такт">
      <span className="logo-mark"><AudioLines size={compact ? 19 : 24} strokeWidth={2.4} /></span>
      <span>такт</span>
    </div>
  );
}

function Onboarding({ inviteCode, onReady }: { inviteCode?: string; onReady: (session: Session) => void }) {
  const [displayName, setDisplayName] = useState("");
  const [roomName, setRoomName] = useState("Моя команда");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const name = cleanName(displayName);
    if (name.length < 2) return setError("Введите имя — хотя бы 2 символа");
    if (!inviteCode && roomName.trim().length < 2) return setError("Назовите чат");
    setLoading(true);
    setError("");

    const { data, error: rpcError } = inviteCode
      ? await supabase.rpc("join_room", { p_invite_code: inviteCode, p_display_name: name })
      : await supabase.rpc("create_room", { p_room_name: roomName.trim(), p_display_name: name });

    if (rpcError || !data?.[0]) {
      setError(rpcError?.message.includes("not found") ? "Ссылка устарела или чат не найден" : (rpcError?.message ?? "Не удалось подключиться. Попробуйте ещё раз"));
      setLoading(false);
      return;
    }
    onReady(toSession(data[0] as RpcSession));
  };

  return (
    <main className="onboarding-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <nav className="landing-nav">
        <Logo compact />
        <span className="privacy-pill"><span /> без Gmail и email</span>
      </nav>
      <section className="onboarding-grid">
        <div className="hero-copy">
          <div className="eyebrow"><Sparkles size={15} /> Пространство для живого общения</div>
          <h1>{inviteCode ? <>Вас уже<br /><em>ждут внутри</em></> : <>Ближе к людям.<br /><em>Без лишнего.</em></>}</h1>
          <p>{inviteCode ? "Откройте чат по приглашению — Gmail, пароль и длинная анкета не нужны." : "Создайте чат, отправьте ссылку и говорите. Сообщения, фото, файлы и чистый звук — без видеокамер и регистрации по почте."}</p>
          <div className="feature-row">
            <span><MessageCircleMore size={17} /> Realtime-чат</span>
            <span><PhoneCall size={17} /> Аудиозвонки</span>
            <span><Paperclip size={17} /> Фото и файлы</span>
            <span><Link2 size={17} /> Вход по ссылке</span>
          </div>
        </div>

        <div className="join-card">
          <div className="brand-art" aria-hidden="true"><div className="art-wave"><AudioLines size={46} /></div></div>
          <div className="card-content">
            <div className="step-label">{inviteCode ? "Приглашение в чат" : "Новая комната"}</div>
            <h2>{inviteCode ? "Как вас называть?" : "Начнём разговор"}</h2>
            <p>{inviteCode ? <>Код приглашения <strong>{inviteCode}</strong></> : "Два поля — и пространство готово."}</p>
            <form onSubmit={submit}>
              <label>
                <span>Ваше имя</span>
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={32} autoFocus placeholder="Например, Алекс" autoComplete="nickname" />
              </label>
              {!inviteCode && (
                <label>
                  <span>Название чата</span>
                  <input value={roomName} onChange={(e) => setRoomName(e.target.value)} maxLength={48} placeholder="Дизайн-команда" />
                </label>
              )}
              {error && <div className="form-error">{error}</div>}
              <button className="primary-button" disabled={loading}>
                {loading ? <span className="button-loader" /> : <>{inviteCode ? "Войти в чат" : "Создать чат"}<ChevronRight size={18} /></>}
              </button>
            </form>
            <small><Check size={13} /> Продолжая, вы создаёте только локальный ключ доступа на этом устройстве.</small>
          </div>
        </div>
      </section>
    </main>
  );
}

function Messenger({ session, onLeave }: { session: Session; onLeave: () => void }) {
  const [state, setState] = useState<RoomState | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [online, setOnline] = useState<Participant[]>([]);
  const [draft, setDraft] = useState("");
  const [typingNames, setTypingNames] = useState<string[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [light, setLight] = useState(false);
  const [toast, setToast] = useState("");
  const [loadError, setLoadError] = useState("");
  const [incoming, setIncoming] = useState<IncomingCall | null>(null);
  const [callStatus, setCallStatus] = useState<"idle" | "calling" | "connected">("idle");
  const [callPeer, setCallPeer] = useState<Participant | null>(null);
  const [muted, setMuted] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const channelRef = useRef<RealtimeChannel | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const pendingCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const messageEndRef = useRef<HTMLDivElement | null>(null);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recordingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const recorderStreamRef = useRef<MediaStream | null>(null);
  const recorderChunksRef = useRef<Blob[]>([]);
  const recordingStartedRef = useRef(0);
  const cancelRecordingRef = useRef(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const lastTypingSentRef = useRef(0);
  const handleSignalRef = useRef<(signal: Signal) => Promise<void>>(async () => undefined);
  const cleanupCallRef = useRef<(notify?: boolean) => void>(() => undefined);

  const showToast = useCallback((text: string) => {
    setToast(text);
    window.setTimeout(() => setToast(""), 2400);
  }, []);

  const track = useCallback(async (eventName: string, properties: Record<string, unknown> = {}) => {
    await supabase.rpc("track_event", {
      p_participant_id: session.participantId,
      p_token: session.participantToken,
      p_event_name: eventName,
      p_properties: properties,
    });
  }, [session]);

  const sendSignal = useCallback(async (signal: Omit<Signal, "from">) => {
    await channelRef.current?.send({ type: "broadcast", event: "webrtc", payload: { ...signal, from: session.participantId } });
  }, [session.participantId]);

  const cleanupCall = useCallback((notify = false) => {
    if (notify && callPeer) void sendSignal({ kind: "call-end", to: callPeer.id });
    peerRef.current?.close();
    peerRef.current = null;
    localStreamRef.current?.getTracks().forEach((trackItem) => trackItem.stop());
    localStreamRef.current = null;
    if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null;
    pendingCandidatesRef.current = [];
    setIncoming(null);
    setCallPeer(null);
    setCallStatus("idle");
    setMuted(false);
  }, [callPeer, sendSignal]);

  const makePeer = useCallback(async (target: Participant) => {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error("Браузер не поддерживает аудиозвонки");
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      video: false,
    });
    localStreamRef.current = stream;
    const pc = new RTCPeerConnection({ iceServers: [{ urls: "stun:stun.l.google.com:19302" }] });
    stream.getAudioTracks().forEach((trackItem) => pc.addTrack(trackItem, stream));
    pc.onicecandidate = (event) => {
      if (event.candidate) void sendSignal({ kind: "ice-candidate", to: target.id, candidate: event.candidate.toJSON() });
    };
    pc.ontrack = (event) => {
      if (remoteAudioRef.current) {
        remoteAudioRef.current.srcObject = event.streams[0];
        void remoteAudioRef.current.play().catch(() => undefined);
      }
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === "connected") {
        setCallStatus("connected");
        void track("call_connected");
      }
      if (["failed", "disconnected", "closed"].includes(pc.connectionState)) {
        if (pc.connectionState === "failed") void track("call_failed", { reason: "peer_connection" });
        cleanupCall(false);
      }
    };
    peerRef.current = pc;
    return pc;
  }, [cleanupCall, sendSignal, track]);

  const handleSignal = useCallback(async (signal: Signal) => {
    if (signal.from === session.participantId || (signal.to && signal.to !== session.participantId)) return;
    const sender = [...(state?.participants ?? []), ...online].find((person) => person.id === signal.from) ?? {
      id: signal.from, display_name: signal.name ?? "Участник", color: "#8b7cff", is_owner: false,
    };
    if (signal.kind === "call-invite" && signal.offer) {
      if (callStatus !== "idle" || incoming) {
        await sendSignal({ kind: "call-decline", to: signal.from });
        return;
      }
      setCallPeer(sender);
      setIncoming({ callerId: signal.from, callerName: signal.name ?? sender.display_name, offer: signal.offer });
    } else if (signal.kind === "call-answer" && signal.answer && peerRef.current) {
      await peerRef.current.setRemoteDescription(signal.answer);
      for (const candidate of pendingCandidatesRef.current.splice(0)) await peerRef.current.addIceCandidate(candidate).catch(() => undefined);
    } else if (signal.kind === "ice-candidate" && signal.candidate) {
      if (peerRef.current?.remoteDescription) await peerRef.current.addIceCandidate(signal.candidate).catch(() => undefined);
      else pendingCandidatesRef.current.push(signal.candidate);
    } else if (signal.kind === "call-decline") {
      showToast("Звонок отклонён");
      cleanupCall(false);
    } else if (signal.kind === "call-end") {
      showToast("Звонок завершён");
      cleanupCall(false);
    }
  }, [callStatus, cleanupCall, incoming, online, sendSignal, session.participantId, showToast, state?.participants]);

  handleSignalRef.current = handleSignal;
  cleanupCallRef.current = cleanupCall;

  useEffect(() => {
    let active = true;
    const load = async () => {
      const { data, error } = await supabase.rpc("get_room_state", {
        p_invite_code: session.inviteCode,
        p_participant_id: session.participantId,
        p_token: session.participantToken,
      });
      if (!active) return;
      if (error || !data) {
        setLoadError("Ключ доступа больше не действует. Откройте приглашение заново.");
        return;
      }
      const roomState = data as RoomState;
      setState(roomState);
      setMessages(roomState.messages ?? []);
    };
    void load();
    return () => { active = false; };
  }, [session]);

  useEffect(() => {
    if (!state) return;
    const channel = supabase.channel(`room:${session.roomId}`, {
      config: { broadcast: { self: false, ack: true }, presence: { key: session.participantId } },
    });
    channelRef.current = channel;
    channel
      .on("broadcast", { event: "message" }, ({ payload }) => {
        const message = (payload.message ?? payload) as Message;
        setMessages((current) => current.some((item) => item.id === message.id) ? current : [...current, message]);
      })
      .on("broadcast", { event: "typing" }, ({ payload }) => {
        if (payload.id === session.participantId) return;
        setTypingNames((current) => Array.from(new Set([...current, payload.name])));
        if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
        typingTimerRef.current = setTimeout(() => setTypingNames([]), 1800);
      })
      .on("broadcast", { event: "webrtc" }, ({ payload }) => { void handleSignalRef.current(payload as Signal); })
      .on("presence", { event: "sync" }, () => {
        const presence = channel.presenceState<Participant>();
        const people = Object.values(presence).flat().map((entry) => ({
          id: entry.id, display_name: entry.display_name, color: entry.color, is_owner: entry.is_owner,
        }));
        setOnline(Array.from(new Map(people.map((person) => [person.id, person])).values()));
      })
      .subscribe((status) => {
        if (status === "SUBSCRIBED") {
          void channel.track({ id: session.participantId, display_name: session.displayName, color: session.color, is_owner: state.me.is_owner });
        }
      });
    return () => {
      void channel.untrack();
      void supabase.removeChannel(channel);
      channelRef.current = null;
      if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
      cleanupCallRef.current(false);
    };
  }, [session.color, session.displayName, session.participantId, session.roomId, state?.me.is_owner]);

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  useEffect(() => () => {
    if (recordingTimerRef.current) clearInterval(recordingTimerRef.current);
    if (recorderRef.current?.state === "recording") {
      cancelRecordingRef.current = true;
      recorderRef.current.stop();
    }
    recorderStreamRef.current?.getTracks().forEach((trackItem) => trackItem.stop());
  }, []);

  const inviteUrl = typeof window === "undefined" ? "" : `${window.location.origin}/join/${session.inviteCode}`;

  const shareInvite = async () => {
    try {
      const canShare = typeof navigator.share === "function";
      if (canShare) await navigator.share({ title: `Чат «${session.roomName}»`, text: "Присоединяйтесь к чату в Такте — почта не нужна", url: inviteUrl });
      else await navigator.clipboard.writeText(inviteUrl);
      showToast(canShare ? "Приглашение открыто" : "Ссылка скопирована");
      void track("invite_shared");
    } catch { /* share sheet dismissed */ }
  };

  const copyInvite = async () => {
    await navigator.clipboard.writeText(inviteUrl);
    showToast("Ссылка скопирована");
    void track("invite_shared", { method: "copy" });
  };

  const sendMessage = async (event: FormEvent) => {
    event.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setDraft("");
    setEmojiOpen(false);
    const { data, error } = await supabase.rpc("send_message", {
      p_participant_id: session.participantId,
      p_token: session.participantToken,
      p_body: body,
    });
    if (error || !data) {
      setDraft(body);
      showToast("Сообщение не отправлено");
      return;
    }
    const message = data as Message;
    setMessages((current) => current.some((item) => item.id === message.id) ? current : [...current, message]);
  };

  const sendAttachment = async (file: File, durationSeconds?: number) => {
    const mimeType = file.type || "application/octet-stream";
    if (!file.size || file.size > MAX_ATTACHMENT_BYTES) {
      showToast("Файл должен быть не больше 8 МБ");
      return;
    }
    if (mimeType.startsWith("video/") || BLOCKED_MIME_TYPES.has(mimeType)) {
      showToast("Этот формат не поддерживается");
      return;
    }
    setUploading(true);
    try {
      const base64 = await fileToBase64(file);
      const { data, error } = await supabase.rpc("send_attachment", {
        p_participant_id: session.participantId,
        p_token: session.participantToken,
        p_file_name: file.name.slice(-120),
        p_mime_type: mimeType,
        p_base64: base64,
        p_duration_seconds: durationSeconds ?? null,
      });
      if (error || !data) throw error ?? new Error("upload failed");
      const message = data as Message;
      setMessages((current) => current.some((item) => item.id === message.id) ? current : [...current, message]);
      showToast(message.kind === "audio" ? "Голосовое отправлено" : "Файл отправлен");
    } catch {
      showToast("Не удалось отправить файл");
    } finally {
      setUploading(false);
    }
  };

  const chooseFile = () => {
    setEmojiOpen(false);
    fileInputRef.current?.click();
  };

  const onFileSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file) void sendAttachment(file);
  };

  const stopRecording = () => {
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
  };

  const cancelRecording = () => {
    cancelRecordingRef.current = true;
    stopRecording();
  };

  const startRecording = async () => {
    if (recording || uploading) return;
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      showToast("Запись голоса не поддерживается");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        video: false,
      });
      const preferredType = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus"]
        .find((type) => MediaRecorder.isTypeSupported(type));
      const recorder = new MediaRecorder(stream, preferredType ? { mimeType: preferredType } : undefined);
      recorderRef.current = recorder;
      recorderStreamRef.current = stream;
      recorderChunksRef.current = [];
      cancelRecordingRef.current = false;
      recordingStartedRef.current = Date.now();
      setRecordSeconds(0);
      setRecording(true);
      recorder.ondataavailable = (event) => {
        if (event.data.size) recorderChunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        const cancelled = cancelRecordingRef.current;
        const seconds = Math.max(1, Math.min(300, Math.ceil((Date.now() - recordingStartedRef.current) / 1000)));
        const mimeType = recorder.mimeType || "audio/webm";
        const extension = mimeType.includes("ogg") ? "ogg" : "webm";
        const blob = new Blob(recorderChunksRef.current, { type: mimeType });
        recorderStreamRef.current?.getTracks().forEach((trackItem) => trackItem.stop());
        recorderStreamRef.current = null;
        recorderRef.current = null;
        recorderChunksRef.current = [];
        if (recordingTimerRef.current) clearInterval(recordingTimerRef.current);
        recordingTimerRef.current = null;
        setRecording(false);
        setRecordSeconds(0);
        if (!cancelled && blob.size) {
          const file = new File([blob], `Голосовое-${new Date().toISOString().slice(11, 19).replaceAll(":", "-")}.${extension}`, { type: mimeType });
          void sendAttachment(file, seconds);
        }
      };
      recorder.start(250);
      recordingTimerRef.current = setInterval(() => {
        const elapsed = Math.floor((Date.now() - recordingStartedRef.current) / 1000);
        setRecordSeconds(Math.min(300, elapsed));
        if (elapsed >= 300 && recorder.state === "recording") recorder.stop();
      }, 500);
    } catch {
      showToast("Разрешите доступ к микрофону");
      recorderStreamRef.current?.getTracks().forEach((trackItem) => trackItem.stop());
      setRecording(false);
    }
  };

  const onDraft = (value: string) => {
    setDraft(value.slice(0, 2000));
    const now = Date.now();
    if (value.trim() && now - lastTypingSentRef.current > 900) {
      lastTypingSentRef.current = now;
      void channelRef.current?.send({ type: "broadcast", event: "typing", payload: { id: session.participantId, name: session.displayName } });
    }
  };

  const startCall = async (target: Participant) => {
    if (callStatus !== "idle" || incoming) return;
    try {
      setCallPeer(target);
      setCallStatus("calling");
      const pc = await makePeer(target);
      const offer = await pc.createOffer({ offerToReceiveAudio: true });
      await pc.setLocalDescription(offer);
      await sendSignal({ kind: "call-invite", to: target.id, name: session.displayName, offer });
      void track("call_started", { direction: "outgoing" });
    } catch (error) {
      showToast(error instanceof DOMException && error.name === "NotAllowedError" ? "Разрешите доступ к микрофону" : "Не удалось начать звонок");
      cleanupCall(false);
    }
  };

  const acceptCall = async () => {
    if (!incoming || !callPeer) return;
    try {
      const current = incoming;
      setIncoming(null);
      setCallStatus("calling");
      const pc = await makePeer(callPeer);
      await pc.setRemoteDescription(current.offer);
      for (const candidate of pendingCandidatesRef.current.splice(0)) await pc.addIceCandidate(candidate).catch(() => undefined);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      await sendSignal({ kind: "call-answer", to: current.callerId, answer });
      void track("call_started", { direction: "incoming" });
    } catch {
      showToast("Разрешите доступ к микрофону");
      await sendSignal({ kind: "call-decline", to: incoming.callerId });
      cleanupCall(false);
    }
  };

  const declineCall = async () => {
    if (incoming) await sendSignal({ kind: "call-decline", to: incoming.callerId });
    cleanupCall(false);
  };

  const toggleMute = () => {
    const trackItem = localStreamRef.current?.getAudioTracks()[0];
    if (!trackItem) return;
    trackItem.enabled = !trackItem.enabled;
    setMuted(!trackItem.enabled);
  };

  const filteredMessages = useMemo(() => search.trim()
    ? messages.filter((message) => message.body.toLowerCase().includes(search.toLowerCase())
      || message.display_name.toLowerCase().includes(search.toLowerCase())
      || message.attachment?.file_name.toLowerCase().includes(search.toLowerCase()))
    : messages, [messages, search]);

  if (loadError) {
    return (
      <main className="fatal-state">
        <Logo />
        <h1>Не получилось открыть чат</h1>
        <p>{loadError}</p>
        <button className="primary-button" onClick={onLeave}><ArrowLeft size={18} /> На главную</button>
      </main>
    );
  }

  if (!state) return <main className="splash"><Logo /><div className="pulse-loader"><i /><i /><i /></div></main>;

  const otherOnline = online.filter((person) => person.id !== session.participantId);

  return (
    <main className={`messenger ${light ? "theme-light" : ""}`}>
      <audio ref={remoteAudioRef} autoPlay />
      <aside className={`sidebar ${sidebarOpen ? "sidebar-open" : ""}`}>
        <div className="sidebar-top">
          <Logo compact />
          <button className="icon-button mobile-only" onClick={() => setSidebarOpen(false)} aria-label="Закрыть меню"><X size={19} /></button>
        </div>
        <button className="new-chat-button" onClick={() => void shareInvite()}><Plus size={17} /> Пригласить человека</button>
        <div className="section-caption"><span>Комната</span><MoreHorizontal size={16} /></div>
        <div className="room-tile active">
          <div className="room-icon"><MessageCircleMore size={20} /></div>
          <div><strong>{session.roomName}</strong><span>{online.length || 1} онлайн</span></div>
          <i>{messages.length || ""}</i>
        </div>
        <div className="section-caption"><span>Сейчас в чате</span><span>{online.length || 1}</span></div>
        <div className="people-list">
          {(online.length ? online : [state.me]).map((person) => (
            <div className="person-row" key={person.id}>
              <Avatar person={person} />
              <div><strong>{person.display_name}{person.id === session.participantId ? " · вы" : ""}</strong><span><i className="online-dot" /> онлайн</span></div>
              {person.id !== session.participantId && (
                <button className="mini-call" onClick={() => void startCall(person)} disabled={callStatus !== "idle"} aria-label={`Позвонить ${person.display_name}`}><Phone size={15} /></button>
              )}
            </div>
          ))}
        </div>
        <div className="sidebar-bottom">
          <button className="profile-button" onClick={() => setLight((value) => !value)}>
            <Avatar person={state.me} />
            <div><strong>{session.displayName}</strong><span>Ваш профиль</span></div>
            {light ? <Moon size={17} /> : <Sun size={17} />}
          </button>
          <button className="leave-link" onClick={onLeave}><ArrowLeft size={15} /> Выйти с устройства</button>
        </div>
      </aside>

      <section className="chat-panel">
        <header className="chat-header">
          <button className="icon-button mobile-only" onClick={() => setSidebarOpen(true)} aria-label="Открыть меню"><Menu size={20} /></button>
          <div className="channel-title">
            <strong>{session.roomName}</strong>
            <span><i className="online-dot" /> {online.length || 1} в сети</span>
          </div>
          <div className="header-actions">
            {searchOpen && <input className="search-input" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Поиск…" autoFocus />}
            <button className={`icon-button ${searchOpen ? "selected" : ""}`} onClick={() => { setSearchOpen((value) => !value); setSearch(""); }} aria-label="Поиск"><Search size={19} /></button>
            <button className="icon-button" onClick={copyInvite} aria-label="Скопировать приглашение"><Link2 size={19} /></button>
            <button className="call-button" disabled={!otherOnline.length || callStatus !== "idle"} onClick={() => otherOnline[0] && void startCall(otherOnline[0])}><Phone size={17} /> <span>Позвонить</span></button>
          </div>
        </header>

        <div className="invite-strip">
          <div><Share2 size={18} /><span>Вход без Gmail и email</span><code>{session.inviteCode}</code></div>
          <button onClick={copyInvite}><Copy size={14} /> Копировать ссылку</button>
        </div>

        <div className="messages" aria-live="polite">
          <div className="chat-intro">
            <div className="intro-icon"><MessageCircleMore size={29} /></div>
            <h2>{session.roomName}</h2>
            <p>Это начало разговора. Пригласите людей по ссылке — регистрация по почте не понадобится.</p>
          </div>
          {filteredMessages.map((message, index) => {
            const mine = message.participant_id === session.participantId;
            const previous = filteredMessages[index - 1];
            const grouped = previous?.participant_id === message.participant_id && new Date(message.created_at).getTime() - new Date(previous.created_at).getTime() < 180000;
            return (
              <article className={`message-row ${mine ? "mine" : ""} ${grouped ? "grouped" : ""}`} key={message.id}>
                {!grouped && !mine ? <Avatar person={{ id: message.participant_id, display_name: message.display_name, color: message.color, is_owner: false }} /> : <span className="avatar-spacer" />}
                <div className="message-content">
                  {!grouped && <div className="message-meta"><strong>{mine ? "Вы" : message.display_name}</strong><time>{formatTime(message.created_at)}</time></div>}
                  {message.kind === "text" || !message.attachment
                    ? <div className="bubble">{message.body}</div>
                    : <AttachmentBubble message={message} session={session} onError={showToast} />}
                </div>
              </article>
            );
          })}
          {search && !filteredMessages.length && <div className="no-results">Ничего не найдено</div>}
          <div ref={messageEndRef} />
        </div>

        <footer className="composer-wrap">
          <div className="typing-line">{uploading ? "Отправляем файл…" : typingNames.length ? `${typingNames.join(", ")} печатает…` : "\u00a0"}</div>
          <input ref={fileInputRef} className="file-input" type="file" accept="image/*,audio/*,.pdf,.txt,.zip,.doc,.docx,.xls,.xlsx,.ppt,.pptx" onChange={onFileSelected} />
          <form className={`composer ${recording ? "is-recording" : ""}`} onSubmit={sendMessage}>
            <button type="button" disabled={recording || uploading} className={`composer-icon ${emojiOpen ? "selected" : ""}`} onClick={() => setEmojiOpen((value) => !value)} aria-label="Эмодзи"><Smile size={21} /></button>
            {emojiOpen && <div className="emoji-popover">{EMOJIS.map((emoji) => <button type="button" key={emoji} onClick={() => { setDraft((value) => value + emoji); setEmojiOpen(false); }}>{emoji}</button>)}</div>}
            <button type="button" disabled={recording || uploading} className="composer-icon" onClick={chooseFile} aria-label="Добавить фото или файл"><Paperclip size={21} /></button>
            <button type="button" disabled={uploading} className={`composer-icon voice-button ${recording ? "recording" : ""}`} onClick={() => recording ? stopRecording() : void startRecording()} aria-label={recording ? "Остановить запись" : "Записать голосовое"}>{recording ? <Square size={17} fill="currentColor" /> : <Mic size={20} />}</button>
            {recording ? (
              <div className="recording-banner"><i /><strong>Запись голоса</strong><span>{formatDuration(recordSeconds)}</span><button type="button" onClick={cancelRecording} aria-label="Отменить запись"><X size={15} /></button></div>
            ) : (
              <textarea value={draft} onChange={(e) => onDraft(e.target.value)} onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); e.currentTarget.form?.requestSubmit(); }
              }} rows={1} maxLength={2000} placeholder="Сообщение…" aria-label="Сообщение" />
            )}
            <span className="char-count">{!recording && draft.length > 1800 ? `${draft.length}/2000` : ""}</span>
            <button className="send-button" disabled={recording || uploading || !draft.trim()} aria-label="Отправить"><Send size={18} /></button>
          </form>
          <div className="composer-hint">Фото и файлы до 8 МБ · голосовые до 5 минут</div>
        </footer>
      </section>

      {incoming && (
        <div className="call-overlay">
          <div className="call-card incoming-card">
            <span className="call-kicker">Входящий аудиозвонок</span>
            <Avatar person={callPeer ?? { id: incoming.callerId, display_name: incoming.callerName, color: "#8b7cff", is_owner: false }} large />
            <h2>{incoming.callerName}</h2>
            <p>Звонит вам · только звук</p>
            <div className="incoming-actions">
              <button className="round-call decline" onClick={() => void declineCall()}><PhoneOff size={22} /></button>
              <button className="round-call accept" onClick={() => void acceptCall()}><Phone size={22} /></button>
            </div>
          </div>
        </div>
      )}

      {callStatus !== "idle" && !incoming && callPeer && (
        <div className="active-call-bar">
          <div className="call-wave"><i /><i /><i /><i /></div>
          <div><strong>{callPeer.display_name}</strong><span>{callStatus === "connected" ? "Аудиозвонок · соединено" : "Соединяем…"}</span></div>
          <button className={muted ? "muted" : ""} onClick={toggleMute} aria-label={muted ? "Включить микрофон" : "Выключить микрофон"}>{muted ? <MicOff size={18} /> : <Mic size={18} />}</button>
          <button className="hangup" onClick={() => { void track("call_ended"); cleanupCall(true); }} aria-label="Завершить звонок"><PhoneOff size={18} /></button>
        </div>
      )}

      {toast && <div className="toast"><Check size={16} /> {toast}</div>}
    </main>
  );
}

function AttachmentBubble({ message, session, onError }: {
  message: Message;
  session: Session;
  onError: (text: string) => void;
}) {
  const attachment = message.attachment as Attachment;
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(message.kind === "image" || message.kind === "audio");

  const fetchAttachment = useCallback(async () => {
    const { data, error } = await supabase.rpc("get_attachment", {
      p_participant_id: session.participantId,
      p_token: session.participantToken,
      p_attachment_id: attachment.id,
    });
    if (error || !data) throw error ?? new Error("attachment not found");
    const payload = data as Attachment & { base64: string };
    return base64ToObjectUrl(payload.base64, payload.mime_type);
  }, [attachment.id, session.participantId, session.participantToken]);

  useEffect(() => {
    if (message.kind !== "image" && message.kind !== "audio") return;
    let active = true;
    let objectUrl = "";
    void fetchAttachment()
      .then((nextUrl) => {
        objectUrl = nextUrl;
        if (active) setUrl(nextUrl);
        else URL.revokeObjectURL(nextUrl);
      })
      .catch(() => active && onError("Не удалось загрузить вложение"))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [fetchAttachment, message.kind, onError]);

  const download = async () => {
    try {
      setLoading(true);
      const downloadUrl = url || await fetchAttachment();
      const anchor = document.createElement("a");
      anchor.href = downloadUrl;
      anchor.download = attachment.file_name;
      anchor.click();
      if (!url) window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000);
    } catch {
      onError("Не удалось скачать файл");
    } finally {
      setLoading(false);
    }
  };

  if (message.kind === "image") {
    return (
      <div className="attachment-bubble image-bubble">
        {url ? <img src={url} alt={attachment.file_name} /> : <div className="media-loader"><ImageIcon size={23} /></div>}
        <div className="attachment-caption"><span><strong>{attachment.file_name}</strong><small>{formatBytes(attachment.size_bytes)}</small></span><button onClick={() => void download()} aria-label="Скачать фото"><Download size={16} /></button></div>
      </div>
    );
  }

  if (message.kind === "audio") {
    return (
      <div className="attachment-bubble audio-bubble">
        <div className="voice-mark"><AudioLines size={21} /></div>
        <div className="voice-body"><strong>Голосовое сообщение</strong>{url ? <audio controls preload="metadata" src={url} /> : <span>{loading ? "Загружаем…" : "Недоступно"}</span>}</div>
        <time>{attachment.duration_seconds ? formatDuration(attachment.duration_seconds) : ""}</time>
      </div>
    );
  }

  return (
    <button className="attachment-bubble file-bubble" onClick={() => void download()} disabled={loading}>
      <span className="file-mark"><FileText size={22} /></span>
      <span><strong>{attachment.file_name}</strong><small>{formatBytes(attachment.size_bytes)}</small></span>
      <Download size={17} />
    </button>
  );
}

function Avatar({ person, large = false }: { person: Participant; large?: boolean }) {
  return <span className={`avatar ${large ? "avatar-large" : ""}`} style={{ "--avatar": person.color } as React.CSSProperties}>{initials(person.display_name)}</span>;
}

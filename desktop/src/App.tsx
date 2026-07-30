import {
  Archive,
  ArchiveRestore,
  Bell,
  Camera,
  CameraOff,
  ChevronDown,
  FileText,
  Hash,
  Headphones,
  ImagePlus,
  Info,
  LoaderCircle,
  LogOut,
  Mic,
  MicOff,
  MonitorOff,
  MonitorUp,
  MoreHorizontal,
  MessageCircle,
  Phone,
  PhoneOff,
  Plus,
  Search,
  SendHorizontal,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Video,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import {
  type CSSProperties,
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  bootstrap,
  endCall,
  getCallCredentials,
  getConversation,
  markRead,
  openDirectChat,
  searchPeople,
  sendAttachment,
  sendText,
  setPresence,
  setTyping,
  startOrJoinCall,
  supabase,
  toggleReaction,
  updateChatSettings,
} from "./api";
import { DesktopCallClient } from "./call-client";
import type {
  ActiveCall,
  BootstrapData,
  CallUiState,
  ChatState,
  ConversationSummary,
  MessengerMessage,
  UserProfile,
} from "./types";

type View = "messages" | "settings";
type ConversationFilter = "all" | "unread" | "archived";
type Notice = { tone: "error" | "success"; text: string } | null;
type JoinedCall = { call: ActiveCall; title: string; conversationId: string };

const idleCall: CallUiState = {
  connected: false,
  muted: false,
  cameraEnabled: false,
  screenSharing: false,
  participantCount: 0,
};

const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "T";

const time = (date: string) =>
  new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(date));

const dateLabel = (date: string) =>
  new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "long",
  }).format(new Date(date));

const friendlyError = (error: unknown) => {
  if (error instanceof Error && error.message) return error.message;
  return "Что-то пошло не так. Попробуйте ещё раз.";
};

function Avatar({
  name,
  color,
  size = "normal",
  online,
}: {
  name: string;
  color?: string;
  size?: "small" | "normal" | "large";
  online?: boolean;
}) {
  return (
    <span
      className={`avatar avatar--${size}`}
      style={{ "--avatar-color": color ?? "#8E7BFF" } as CSSProperties}
      aria-label={name}
    >
      {initials(name)}
      {online && <i className="online-dot" />}
    </span>
  );
}

function IconButton({
  label,
  active = false,
  danger = false,
  disabled = false,
  children,
  onClick,
}: {
  label: string;
  active?: boolean;
  danger?: boolean;
  disabled?: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`icon-button${active ? " is-active" : ""}${danger ? " is-danger" : ""}`}
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function Splash() {
  return (
    <main className="splash">
      <div className="takt-mark takt-mark--large">T</div>
      <div>
        <strong>Такт</strong>
        <span>Запускаем рабочее пространство…</span>
      </div>
    </main>
  );
}

function AuthScreen({
  onComplete,
}: {
  onComplete: () => Promise<void>;
}) {
  const [mode, setMode] = useState<"sign-in" | "sign-up">("sign-in");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      if (mode === "sign-in") {
        const { error: authError } = await supabase.auth.signInWithPassword({
          email: email.trim(),
          password,
        });
        if (authError) throw authError;
      } else {
        if (displayName.trim().length < 2) {
          throw new Error("Введите имя — хотя бы 2 символа.");
        }
        const { data, error: authError } = await supabase.auth.signUp({
          email: email.trim(),
          password,
          options: { data: { display_name: displayName.trim() } },
        });
        if (authError) throw authError;
        if (!data.session) {
          setNotice("Проверьте почту и подтвердите аккаунт, затем войдите.");
          return;
        }
      }
      await onComplete();
    } catch (submitError) {
      setError(friendlyError(submitError));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-layout">
      <section className="auth-art">
        <div className="auth-grid" />
        <div className="auth-brand">
          <div className="takt-mark takt-mark--large">T</div>
          <span className="eyebrow">ТАКТ ДЛЯ WINDOWS</span>
          <h1>Чаты и голос<br />в одном ритме.</h1>
          <p>
            Войдите тем же аккаунтом, что на телефоне. Сообщения, чаты и
            голосовые комнаты останутся общими.
          </p>
          <div className="auth-feature-list">
            <span><ShieldCheck size={18} /> Защищённый вход</span>
            <span><Headphones size={18} /> Общий голосовой канал</span>
            <span><Sparkles size={18} /> Свой дизайн «Такт»</span>
          </div>
        </div>
      </section>

      <section className="auth-panel">
        <form className="auth-card" onSubmit={submit}>
          <div className="auth-card__top">
            <div className="takt-mark">T</div>
            <span className="eyebrow">АККАУНТ</span>
            <h2>{mode === "sign-in" ? "С возвращением" : "Создать аккаунт"}</h2>
            <p>
              {mode === "sign-in"
                ? "Войдите в свой Тakt‑аккаунт."
                : "Один профиль для телефона и компьютера."}
            </p>
          </div>

          {mode === "sign-up" && (
            <label className="field">
              <span>Имя в Такт</span>
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Как вас показать в чатах"
                autoComplete="name"
                minLength={2}
                required
              />
            </label>
          )}
          <label className="field">
            <span>Почта</span>
            <input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              type="email"
              required
            />
          </label>
          <label className="field">
            <span>Пароль</span>
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Не меньше 6 символов"
              autoComplete={mode === "sign-in" ? "current-password" : "new-password"}
              type="password"
              minLength={6}
              required
            />
          </label>

          {error && <p className="form-note form-note--error">{error}</p>}
          {notice && <p className="form-note form-note--success">{notice}</p>}

          <button className="primary-button primary-button--wide" type="submit" disabled={busy}>
            {busy ? <LoaderCircle className="spin" size={18} /> : null}
            {mode === "sign-in" ? "Войти в Такт" : "Создать аккаунт"}
          </button>
          <button
            className="text-button"
            type="button"
            onClick={() => {
              setMode((value) => (value === "sign-in" ? "sign-up" : "sign-in"));
              setError(null);
              setNotice(null);
            }}
          >
            {mode === "sign-in" ? "Нет аккаунта? Создать" : "Уже есть аккаунт? Войти"}
          </button>
        </form>
      </section>
    </main>
  );
}

function App() {
  const calls = useRef<DesktopCallClient | null>(null);
  if (!calls.current) calls.current = new DesktopCallClient();

  const [authReady, setAuthReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [workspace, setWorkspace] = useState<BootstrapData | null>(null);
  const [workspaceBusy, setWorkspaceBusy] = useState(false);
  const [activeView, setActiveView] = useState<View>("messages");
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [activeChat, setActiveChat] = useState<ChatState | null>(null);
  const [chatBusy, setChatBusy] = useState(false);
  const [filter, setFilter] = useState<ConversationFilter>("all");
  const [sidebarQuery, setSidebarQuery] = useState("");
  const [draft, setDraft] = useState("");
  const [replyTo, setReplyTo] = useState<MessengerMessage | null>(null);
  const [sending, setSending] = useState(false);
  const [attachmentBusy, setAttachmentBusy] = useState(false);
  const [newChatOpen, setNewChatOpen] = useState(false);
  const [chatInfoOpen, setChatInfoOpen] = useState(false);
  const [chatMenuOpen, setChatMenuOpen] = useState(false);
  const [peopleQuery, setPeopleQuery] = useState("");
  const [people, setPeople] = useState<UserProfile[]>([]);
  const [peopleBusy, setPeopleBusy] = useState(false);
  const [callBusy, setCallBusy] = useState(false);
  const [callState, setCallState] = useState<CallUiState>(idleCall);
  const [joinedCall, setJoinedCall] = useState<JoinedCall | null>(null);
  const [volume, setVolume] = useState(0.82);
  const [lastAudibleVolume, setLastAudibleVolume] = useState(0.82);
  const [notice, setNotice] = useState<Notice>(null);
  const uploadInput = useRef<HTMLInputElement>(null);
  const typingTimer = useRef<number | null>(null);

  const showNotice = useCallback((tone: Notice["tone"], text: string) => {
    setNotice({ tone, text });
  }, []);

  const refreshWorkspace = useCallback(async () => {
    setWorkspaceBusy(true);
    try {
      const data = await bootstrap();
      setWorkspace(data);
      setActiveConversationId((current) => current ?? data.conversations[0]?.id ?? null);
    } catch (loadError) {
      showNotice("error", friendlyError(loadError));
    } finally {
      setWorkspaceBusy(false);
    }
  }, [showNotice]);

  const loadChat = useCallback(
    async (conversationId: string, showLoading = true) => {
      if (showLoading) setChatBusy(true);
      try {
        const data = await getConversation(conversationId);
        setActiveChat(data);
        void markRead(conversationId);
      } catch (loadError) {
        showNotice("error", friendlyError(loadError));
      } finally {
        if (showLoading) setChatBusy(false);
      }
    },
    [showNotice],
  );

  useEffect(() => {
    let mounted = true;
    void supabase.auth.getSession().then(({ data }) => {
      if (!mounted) return;
      const signedIn = Boolean(data.session);
      setAuthenticated(signedIn);
      setAuthReady(true);
      if (signedIn) void refreshWorkspace();
    });
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      if (!mounted) return;
      const signedIn = Boolean(session);
      setAuthenticated(signedIn);
      if (signedIn) window.setTimeout(() => void refreshWorkspace(), 0);
      if (!signedIn) {
        setWorkspace(null);
        setActiveChat(null);
        setActiveConversationId(null);
      }
    });
    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, [refreshWorkspace]);

  useEffect(() => {
    if (!authenticated) return;
    void setPresence(true);
    const heartbeat = window.setInterval(() => void setPresence(true), 50_000);
    return () => {
      window.clearInterval(heartbeat);
      void setPresence(false);
    };
  }, [authenticated]);

  useEffect(() => {
    if (!authenticated || !activeConversationId) {
      setActiveChat(null);
      return;
    }
    void loadChat(activeConversationId);
  }, [activeConversationId, authenticated, loadChat]);

  useEffect(() => {
    if (!authenticated || !activeConversationId) return;
    const refresh = window.setInterval(() => {
      void loadChat(activeConversationId, false);
      void refreshWorkspace();
    }, 9_000);
    return () => window.clearInterval(refresh);
  }, [activeConversationId, authenticated, loadChat, refreshWorkspace]);

  useEffect(() => {
    if (!newChatOpen || peopleQuery.trim().length < 2) {
      setPeople([]);
      return;
    }
    const timer = window.setTimeout(() => {
      setPeopleBusy(true);
      void searchPeople(peopleQuery.trim())
        .then(setPeople)
        .catch((searchError) => showNotice("error", friendlyError(searchError)))
        .finally(() => setPeopleBusy(false));
    }, 320);
    return () => window.clearTimeout(timer);
  }, [newChatOpen, peopleQuery, showNotice]);

  useEffect(() => {
    return () => {
      if (typingTimer.current) window.clearTimeout(typingTimer.current);
      calls.current?.disconnect();
    };
  }, []);

  const activeSummary = useMemo(
    () => workspace?.conversations.find((chat) => chat.id === activeConversationId) ?? null,
    [activeConversationId, workspace?.conversations],
  );

  const visibleConversations = useMemo(() => {
    const query = sidebarQuery.trim().toLowerCase();
    return (workspace?.conversations ?? [])
      .filter((chat) => {
        if (filter === "unread" && !(chat.unread_count && chat.unread_count > 0)) return false;
        if (filter === "archived" && !chat.is_archived) return false;
        if (filter !== "archived" && chat.is_archived) return false;
        return !query || chat.title.toLowerCase().includes(query);
      })
      .sort((left, right) => Number(Boolean(right.is_pinned)) - Number(Boolean(left.is_pinned)));
  }, [filter, sidebarQuery, workspace?.conversations]);

  const selectChat = (conversationId: string) => {
    setActiveView("messages");
    setActiveConversationId(conversationId);
    setChatMenuOpen(false);
  };

  const handleComposerChange = (value: string) => {
    setDraft(value);
    if (!activeChat || !value.trim()) return;
    void setTyping(activeChat.id, "typing");
    if (typingTimer.current) window.clearTimeout(typingTimer.current);
    typingTimer.current = window.setTimeout(() => {
      if (activeChat) void setTyping(activeChat.id, "idle");
    }, 1800);
  };

  const submitMessage = async () => {
    const body = draft.trim();
    if (!body || !activeChat || sending) return;
    const chatId = activeChat.id;
    const localId = `local-${crypto.randomUUID()}`;
    const optimistic: MessengerMessage = {
      id: localId,
      conversation_id: chatId,
      sender_id: workspace?.profile.id ?? "me",
      sender_name: workspace?.profile.display_name ?? "Вы",
      sender_color: workspace?.profile.avatar_color,
      body,
      kind: "text",
      created_at: new Date().toISOString(),
      status: "sending",
      reply_to_id: replyTo?.id ?? null,
      reply_preview: replyTo
        ? {
            id: replyTo.id,
            body: replyTo.body,
            kind: replyTo.kind,
            sender_name: replyTo.sender_name,
          }
        : null,
    };
    setSending(true);
    setDraft("");
    setReplyTo(null);
    setActiveChat((previous) =>
      previous ? { ...previous, messages: [...previous.messages, optimistic] } : previous,
    );
    try {
      const saved = await sendText(chatId, body, replyTo?.id ?? null);
      setActiveChat((previous) =>
        previous
          ? {
              ...previous,
              messages: previous.messages.map((message) => (message.id === localId ? saved : message)),
            }
          : previous,
      );
      void setTyping(chatId, "idle");
      void refreshWorkspace();
    } catch (sendError) {
      setActiveChat((previous) =>
        previous
          ? { ...previous, messages: previous.messages.filter((message) => message.id !== localId) }
          : previous,
      );
      showNotice("error", friendlyError(sendError));
    } finally {
      setSending(false);
    }
  };

  const composerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void submitMessage();
    }
  };

  const uploadFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !activeChat || attachmentBusy) return;
    if (file.size > 15 * 1024 * 1024) {
      showNotice("error", "Пока можно отправить файл до 15 МБ.");
      return;
    }
    setAttachmentBusy(true);
    try {
      const message = await sendAttachment(activeChat.id, file);
      setActiveChat((previous) =>
        previous ? { ...previous, messages: [...previous.messages, message] } : previous,
      );
      void refreshWorkspace();
    } catch (uploadError) {
      showNotice("error", friendlyError(uploadError));
    } finally {
      setAttachmentBusy(false);
    }
  };

  const reactTo = async (message: MessengerMessage) => {
    try {
      const updated = await toggleReaction(message.id, "👍");
      setActiveChat((previous) =>
        previous
          ? {
              ...previous,
              messages: previous.messages.map((item) => (item.id === updated.id ? updated : item)),
            }
          : previous,
      );
    } catch (reactionError) {
      showNotice("error", friendlyError(reactionError));
    }
  };

  const toggleChatMute = async () => {
    if (!activeSummary) return;
    try {
      const muted = Boolean(activeSummary.muted_until);
      await updateChatSettings(activeSummary, {
        mutedUntil: muted ? null : new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
      });
      setChatMenuOpen(false);
      await refreshWorkspace();
      showNotice("success", muted ? "Уведомления включены." : "Чат без звука на 8 часов.");
    } catch (settingsError) {
      showNotice("error", friendlyError(settingsError));
    }
  };

  const toggleArchive = async () => {
    if (!activeSummary) return;
    try {
      await updateChatSettings(activeSummary, { archived: !activeSummary.is_archived });
      setChatMenuOpen(false);
      setActiveConversationId(null);
      await refreshWorkspace();
      showNotice("success", activeSummary.is_archived ? "Чат возвращён из архива." : "Чат перенесён в архив.");
    } catch (settingsError) {
      showNotice("error", friendlyError(settingsError));
    }
  };

  const openPersonChat = async (person: UserProfile) => {
    setPeopleBusy(true);
    try {
      const chat = await openDirectChat(person.id);
      setNewChatOpen(false);
      setPeopleQuery("");
      setActiveConversationId(chat.id);
      setActiveChat(chat);
      await refreshWorkspace();
    } catch (openError) {
      showNotice("error", friendlyError(openError));
    } finally {
      setPeopleBusy(false);
    }
  };

  const joinVoice = async (video = false) => {
    if (!activeChat || callBusy) return;
    setCallBusy(true);
    try {
      const call = activeChat.active_call ?? (await startOrJoinCall(activeChat.id, video));
      const credentials = await getCallCredentials(call.id);
      await calls.current?.connect(credentials, setCallState);
      calls.current?.setOutputVolume(volume);
      setJoinedCall({ call, title: activeChat.title, conversationId: activeChat.id });
      setActiveChat((previous) => (previous ? { ...previous, active_call: call } : previous));
      showNotice("success", "Вы в голосовом канале.");
    } catch (callError) {
      showNotice("error", friendlyError(callError));
    } finally {
      setCallBusy(false);
    }
  };

  const leaveVoice = () => {
    calls.current?.disconnect();
    setJoinedCall(null);
    setCallState(idleCall);
  };

  const endVoiceForEveryone = async () => {
    if (!joinedCall) return;
    try {
      await endCall(joinedCall.call.id);
      leaveVoice();
      setActiveChat((previous) => (previous ? { ...previous, active_call: null } : previous));
      showNotice("success", "Звонок завершён для всех участников.");
      void refreshWorkspace();
    } catch (callError) {
      showNotice("error", friendlyError(callError));
    }
  };

  const toggleMute = async () => {
    try {
      await calls.current?.setMuted(!callState.muted);
    } catch (callError) {
      showNotice("error", friendlyError(callError));
    }
  };

  const toggleCamera = async () => {
    try {
      await calls.current?.setCameraEnabled(!callState.cameraEnabled);
    } catch (callError) {
      showNotice("error", friendlyError(callError));
    }
  };

  const toggleScreenShare = async () => {
    try {
      await calls.current?.setScreenShareEnabled(!callState.screenSharing);
    } catch (callError) {
      showNotice("error", friendlyError(callError));
    }
  };

  const changeVolume = (next: number) => {
    setVolume(next);
    if (next > 0) setLastAudibleVolume(next);
    calls.current?.setOutputVolume(next);
  };

  const toggleDeafen = () => {
    changeVolume(volume === 0 ? lastAudibleVolume : 0);
  };

  const signOut = async () => {
    leaveVoice();
    await supabase.auth.signOut();
  };

  if (!authReady) return <Splash />;
  if (!authenticated) return <AuthScreen onComplete={refreshWorkspace} />;

  const profile = workspace?.profile;
  const memberCount = activeChat?.members.length ?? 0;
  const activeCall = activeChat?.active_call ?? (joinedCall?.conversationId === activeChat?.id ? joinedCall.call : null);

  return (
    <main className="desktop-shell">
      <aside className="rail" aria-label="Основная навигация">
        <button className="rail-logo" type="button" onClick={() => setActiveView("messages")} aria-label="Такт">
          <span className="takt-mark">T</span>
        </button>
        <div className="rail-rule" />
        <button
          type="button"
          className={`rail-item${activeView === "messages" ? " is-selected" : ""}`}
          onClick={() => setActiveView("messages")}
          title="Сообщения"
          aria-label="Сообщения"
        >
          <MessageCircle size={21} />
          <i />
        </button>
        <button
          type="button"
          className={`rail-item${activeView === "settings" ? " is-selected" : ""}`}
          onClick={() => setActiveView("settings")}
          title="Настройки"
          aria-label="Настройки"
        >
          <Settings2 size={21} />
        </button>
        <div className="rail-spacer" />
        <button
          type="button"
          className="rail-item rail-item--profile"
          onClick={() => setActiveView("settings")}
          title="Мой профиль"
          aria-label="Мой профиль"
        >
          <Avatar name={profile?.display_name ?? "Вы"} color={profile?.avatar_color} size="small" online />
        </button>
      </aside>

      <aside className="conversations-panel">
        <header className="workspace-title">
          <button type="button" onClick={() => setActiveView("messages")}>
            <span>Такт</span>
            <ChevronDown size={16} />
          </button>
          <IconButton label="Новый диалог" onClick={() => setNewChatOpen(true)}>
            <Plus size={18} />
          </IconButton>
        </header>
        <div className="sidebar-search">
          <Search size={16} />
          <input
            value={sidebarQuery}
            onChange={(event) => setSidebarQuery(event.target.value)}
            placeholder="Поиск чатов"
            aria-label="Поиск чатов"
          />
        </div>
        <nav className="conversation-filters" aria-label="Фильтр чатов">
          {([
            ["all", "Все"],
            ["unread", "Новые"],
            ["archived", "Архив"],
          ] as const).map(([value, label]) => (
            <button
              type="button"
              key={value}
              className={filter === value ? "is-selected" : ""}
              onClick={() => setFilter(value)}
            >
              {label}
            </button>
          ))}
        </nav>
        <div className="conversation-list">
          {workspaceBusy && !workspace && <p className="quiet-state">Загружаем чаты…</p>}
          {!workspaceBusy && visibleConversations.length === 0 && (
            <div className="empty-list">
              <MessageCircle size={21} />
              <p>Здесь пока тихо.</p>
              <button type="button" onClick={() => setNewChatOpen(true)}>Начать диалог</button>
            </div>
          )}
          {visibleConversations.map((conversation) => (
            <button
              type="button"
              className={`conversation-row${activeConversationId === conversation.id ? " is-selected" : ""}`}
              onClick={() => selectChat(conversation.id)}
              key={conversation.id}
            >
              <Avatar
                name={conversation.title}
                color={conversation.avatar_color}
                online={activeConversationId === conversation.id && activeChat?.members.some((member) => member.is_online)}
              />
              <span className="conversation-row__body">
                <span className="conversation-row__title">
                  {conversation.is_pinned && <span className="pin-dot" title="Закреплённый чат" />}
                  {conversation.title}
                </span>
                <span className="conversation-row__preview">
                  {conversation.last_message?.body || conversation.draft_text || "Нет сообщений"}
                </span>
              </span>
              <span className="conversation-row__meta">
                {conversation.unread_count && conversation.unread_count > 0 ? (
                  <b>{conversation.unread_count > 99 ? "99+" : conversation.unread_count}</b>
                ) : (
                  <time>{conversation.last_message ? time(conversation.last_message.created_at) : ""}</time>
                )}
              </span>
            </button>
          ))}
        </div>

        <footer className="account-strip">
          <Avatar name={profile?.display_name ?? "Вы"} color={profile?.avatar_color} size="small" online />
          <button type="button" className="account-strip__identity" onClick={() => setActiveView("settings")}>
            <strong>{profile?.display_name ?? "Пользователь"}</strong>
            <span>{callState.connected ? "В голосовом канале" : "В сети"}</span>
          </button>
          <IconButton label={callState.muted ? "Включить микрофон" : "Выключить микрофон"} active={callState.muted} disabled={!callState.connected} onClick={() => void toggleMute()}>
            {callState.muted ? <MicOff size={17} /> : <Mic size={17} />}
          </IconButton>
          <IconButton label={volume === 0 ? "Включить звук" : "Отключить звук"} active={volume === 0} disabled={!callState.connected} onClick={toggleDeafen}>
            {volume === 0 ? <VolumeX size={17} /> : <Headphones size={17} />}
          </IconButton>
          <IconButton label="Настройки" onClick={() => setActiveView("settings")}>
            <Settings2 size={17} />
          </IconButton>
        </footer>
      </aside>

      {activeView === "settings" ? (
        <SettingsView
          profile={profile}
          volume={volume}
          callConnected={callState.connected}
          onVolume={changeVolume}
          onBack={() => setActiveView("messages")}
          onSignOut={() => void signOut()}
        />
      ) : (
        <>
          <section className="chat-panel">
            {activeChat ? (
              <>
                <header className="chat-header">
                  <Avatar
                    name={activeChat.title}
                    color={activeChat.avatar_color}
                    online={activeChat.members.some((member) => member.is_online)}
                  />
                  <button className="chat-header__identity" type="button" onClick={() => setChatInfoOpen(true)}>
                    <strong>{activeChat.title}</strong>
                    <span>
                      {activeChat.kind === "group"
                        ? `${memberCount} участника`
                        : activeChat.members.find((member) => member.id !== profile?.id)?.is_online
                          ? "в сети"
                          : "личный чат"}
                    </span>
                  </button>
                  <div className="chat-header__actions">
                    <IconButton label="Аудиозвонок" disabled={callBusy} onClick={() => void joinVoice(false)}>
                      {callBusy ? <LoaderCircle className="spin" size={18} /> : <Phone size={18} />}
                    </IconButton>
                    <IconButton label="Видеозвонок" disabled={callBusy} onClick={() => void joinVoice(true)}>
                      <Video size={18} />
                    </IconButton>
                    <IconButton label="Открыть сведения" onClick={() => setChatInfoOpen(true)}>
                      <Info size={18} />
                    </IconButton>
                    <span className="menu-anchor">
                      <IconButton label="Действия чата" active={chatMenuOpen} onClick={() => setChatMenuOpen((open) => !open)}>
                        <MoreHorizontal size={19} />
                      </IconButton>
                      {chatMenuOpen && (
                        <span className="context-menu context-menu--header">
                          <button type="button" onClick={() => void toggleChatMute()}>
                            {activeSummary?.muted_until ? <Bell size={16} /> : <VolumeX size={16} />}
                            {activeSummary?.muted_until ? "Включить уведомления" : "Без звука на 8 часов"}
                          </button>
                          <button type="button" onClick={() => void toggleArchive()}>
                            {activeSummary?.is_archived ? <ArchiveRestore size={16} /> : <Archive size={16} />}
                            {activeSummary?.is_archived ? "Вернуть из архива" : "В архив"}
                          </button>
                        </span>
                      )}
                    </span>
                  </div>
                </header>

                {activeCall && (
                  <div className="call-ribbon">
                    <span className="live-dot" />
                    <div>
                      <strong>Идёт голосовой канал</strong>
                      <span>{activeCall.is_video ? "Видео и голос доступны" : "Голос доступен"}</span>
                    </div>
                    <button className="secondary-button" type="button" disabled={callBusy} onClick={() => void joinVoice(activeCall.is_video)}>
                      <Headphones size={16} />
                      {callState.connected ? "Подключено" : "Присоединиться"}
                    </button>
                  </div>
                )}

                <div className="message-scroller">
                  {chatBusy && activeChat.messages.length === 0 ? (
                    <div className="chat-loading"><LoaderCircle className="spin" size={22} /> Загружаем переписку…</div>
                  ) : activeChat.messages.length === 0 ? (
                    <div className="chat-empty">
                      <Avatar name={activeChat.title} color={activeChat.avatar_color} size="large" />
                      <h2>Это начало чата</h2>
                      <p>Напишите первое сообщение в «{activeChat.title}».</p>
                    </div>
                  ) : (
                    activeChat.messages.map((message, index) => {
                      const previous = activeChat.messages[index - 1];
                      const showDate =
                        !previous ||
                        new Date(previous.created_at).toDateString() !== new Date(message.created_at).toDateString();
                      return (
                        <div key={message.id}>
                          {showDate && <div className="date-divider"><span>{dateLabel(message.created_at)}</span></div>}
                          <MessageBubble
                            message={message}
                            mine={message.sender_id === profile?.id}
                            onReply={() => setReplyTo(message)}
                            onReaction={() => void reactTo(message)}
                          />
                        </div>
                      );
                    })
                  )}
                  {activeChat.typing.length > 0 && (
                    <p className="typing-indicator">
                      <span><i /><i /><i /></span>
                      {activeChat.typing.map((entry) => entry.display_name).join(", ")} печатает…
                    </p>
                  )}
                </div>

                <div className="composer-zone">
                  {replyTo && (
                    <div className="reply-strip">
                      <span />
                      <div>
                        <strong>Ответ для {replyTo.sender_name}</strong>
                        <p>{replyTo.body || "Вложение"}</p>
                      </div>
                      <IconButton label="Отменить ответ" onClick={() => setReplyTo(null)}>
                        <X size={16} />
                      </IconButton>
                    </div>
                  )}
                  <div className="composer">
                    <input
                      ref={uploadInput}
                      type="file"
                      tabIndex={-1}
                      onChange={(event) => void uploadFile(event)}
                      hidden
                    />
                    <IconButton label="Прикрепить файл" disabled={attachmentBusy} onClick={() => uploadInput.current?.click()}>
                      {attachmentBusy ? <LoaderCircle className="spin" size={19} /> : <ImagePlus size={19} />}
                    </IconButton>
                    <textarea
                      rows={1}
                      value={draft}
                      onChange={(event) => handleComposerChange(event.target.value)}
                      onKeyDown={composerKeyDown}
                      placeholder={`Написать в «${activeChat.title}»`}
                      aria-label="Сообщение"
                    />
                    <button
                      className="send-button"
                      type="button"
                      disabled={!draft.trim() || sending}
                      aria-label="Отправить сообщение"
                      onClick={() => void submitMessage()}
                    >
                      {sending ? <LoaderCircle className="spin" size={18} /> : <SendHorizontal size={18} />}
                    </button>
                  </div>
                  <p className="composer-hint">Enter — отправить · Shift + Enter — новая строка</p>
                </div>
              </>
            ) : (
              <EmptyChat onNewChat={() => setNewChatOpen(true)} />
            )}
          </section>

          <aside className="voice-panel">
            <header className="voice-panel__header">
              <span>ГОЛОС И УЧАСТНИКИ</span>
              <SlidersHorizontal size={16} />
            </header>
            <section className={`voice-room${callState.connected ? " is-connected" : ""}`}>
              <div className="voice-room__top">
                <span className="voice-room__glyph"><Hash size={16} /></span>
                <div>
                  <strong>{joinedCall?.title ?? activeChat?.title ?? "Голосовой канал"}</strong>
                  <span>{callState.connected ? `${callState.participantCount} в канале` : "Можно подключиться"}</span>
                </div>
                {callState.connected && <span className="voice-live">LIVE</span>}
              </div>
              <div className="voice-room__participants">
                <div className="voice-person">
                  <Avatar name={profile?.display_name ?? "Вы"} color={profile?.avatar_color} size="small" online />
                  <span>Вы</span>
                  {callState.muted && <MicOff size={15} />}
                </div>
                {activeChat?.members
                  .filter((member) => member.id !== profile?.id && member.is_online)
                  .slice(0, 4)
                  .map((member) => (
                    <div className="voice-person" key={member.id}>
                      <Avatar name={member.display_name} color={member.avatar_color} size="small" online />
                      <span>{member.display_name}</span>
                    </div>
                  ))}
              </div>
              {callState.connected ? (
                <div className="voice-room__controls">
                  <IconButton label={callState.muted ? "Включить микрофон" : "Выключить микрофон"} active={callState.muted} onClick={() => void toggleMute()}>
                    {callState.muted ? <MicOff size={18} /> : <Mic size={18} />}
                  </IconButton>
                  <IconButton label={callState.cameraEnabled ? "Выключить камеру" : "Включить камеру"} active={callState.cameraEnabled} onClick={() => void toggleCamera()}>
                    {callState.cameraEnabled ? <Camera size={18} /> : <CameraOff size={18} />}
                  </IconButton>
                  <IconButton label={callState.screenSharing ? "Остановить демонстрацию" : "Демонстрация экрана"} active={callState.screenSharing} onClick={() => void toggleScreenShare()}>
                    {callState.screenSharing ? <MonitorOff size={18} /> : <MonitorUp size={18} />}
                  </IconButton>
                  <IconButton label="Выйти из канала" danger onClick={leaveVoice}>
                    <PhoneOff size={18} />
                  </IconButton>
                </div>
              ) : (
                <button className="primary-button primary-button--wide" type="button" disabled={!activeChat || callBusy} onClick={() => void joinVoice(false)}>
                  {callBusy ? <LoaderCircle className="spin" size={18} /> : <Headphones size={18} />}
                  Подключиться
                </button>
              )}
            </section>

            <section className="mixer-card">
              <div className="mixer-card__label">
                <span><Volume2 size={16} /> Громкость канала</span>
                <b>{Math.round(volume * 100)}%</b>
              </div>
              <input
                className="volume-slider"
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={volume}
                onChange={(event) => changeVolume(Number(event.target.value))}
                aria-label="Громкость голосового канала"
              />
              <div className="mixer-card__actions">
                <button type="button" className={volume === 0 ? "is-active" : ""} onClick={toggleDeafen}>
                  {volume === 0 ? <VolumeX size={16} /> : <Headphones size={16} />}
                  {volume === 0 ? "Включить звук" : "Отключить звук"}
                </button>
                {callState.connected && (
                  <button type="button" className="danger-action" onClick={() => void endVoiceForEveryone()}>
                    <PhoneOff size={16} /> Завершить всем
                  </button>
                )}
              </div>
            </section>

            <section className="members-card">
              <div className="section-label">
                <span>В ЭТОМ ЧАТЕ</span>
                <b>{memberCount}</b>
              </div>
              {(activeChat?.members ?? []).slice(0, 8).map((member) => (
                <div className="member-row" key={member.id}>
                  <Avatar name={member.display_name} color={member.avatar_color} size="small" online={member.is_online} />
                  <span>{member.id === profile?.id ? "Вы" : member.display_name}</span>
                  {member.role === "owner" && <ShieldCheck size={14} title="Создатель" />}
                </div>
              ))}
            </section>
          </aside>
        </>
      )}

      {notice && (
        <div className={`toast toast--${notice.tone}`} role="status">
          {notice.tone === "success" ? <ShieldCheck size={18} /> : <Info size={18} />}
          <span>{notice.text}</span>
          <button type="button" onClick={() => setNotice(null)} aria-label="Закрыть уведомление"><X size={16} /></button>
        </div>
      )}

      {newChatOpen && (
        <Modal title="Новый диалог" onClose={() => setNewChatOpen(false)}>
          <p className="modal-copy">Найдите пользователя по имени или @username. Поиск видит только доступные вам профили.</p>
          <div className="modal-search">
            <Search size={17} />
            <input
              autoFocus
              value={peopleQuery}
              onChange={(event) => setPeopleQuery(event.target.value)}
              placeholder="Имя или username"
            />
          </div>
          {peopleBusy && <p className="modal-state"><LoaderCircle className="spin" size={18} /> Ищем людей…</p>}
          {!peopleBusy && peopleQuery.trim().length >= 2 && people.length === 0 && (
            <p className="modal-state">Никого не нашли. Проверьте запрос.</p>
          )}
          <div className="people-results">
            {people.filter((person) => person.id !== profile?.id).map((person) => (
              <button type="button" key={person.id} onClick={() => void openPersonChat(person)}>
                <Avatar name={person.display_name} color={person.avatar_color} online={person.is_online} />
                <span>
                  <strong>{person.display_name}</strong>
                  <small>{person.username ? `@${person.username}` : "Пользователь Такт"}</small>
                </span>
                <MessageCircle size={18} />
              </button>
            ))}
          </div>
        </Modal>
      )}

      {chatInfoOpen && activeChat && (
        <Modal title={activeChat.kind === "group" ? "О группе" : "О чате"} onClose={() => setChatInfoOpen(false)}>
          <div className="chat-info-hero">
            <Avatar name={activeChat.title} color={activeChat.avatar_color} size="large" />
            <div>
              <h3>{activeChat.title}</h3>
              <p>{activeChat.kind === "group" ? `${memberCount} участника` : "Личный диалог в Такт"}</p>
            </div>
          </div>
          <div className="info-actions">
            <button type="button" onClick={() => void joinVoice(false)}><Phone size={17} /> Позвонить</button>
            <button type="button" onClick={() => void toggleChatMute()}><Bell size={17} /> {activeSummary?.muted_until ? "Включить звук" : "Без звука"}</button>
            <button type="button" onClick={() => void toggleArchive()}><Archive size={17} /> {activeSummary?.is_archived ? "Вернуть" : "Архивировать"}</button>
          </div>
          <div className="modal-members">
            <p className="section-label"><span>УЧАСТНИКИ</span><b>{memberCount}</b></p>
            {activeChat.members.map((member) => (
              <div className="member-row" key={member.id}>
                <Avatar name={member.display_name} color={member.avatar_color} size="small" online={member.is_online} />
                <span>{member.id === profile?.id ? "Вы" : member.display_name}</span>
                {member.username && <small>@{member.username}</small>}
              </div>
            ))}
          </div>
        </Modal>
      )}
    </main>
  );
}

function EmptyChat({ onNewChat }: { onNewChat: () => void }) {
  return (
    <section className="empty-chat">
      <div className="empty-chat__orb"><MessageCircle size={34} /></div>
      <span className="eyebrow">ТАКТ ДЛЯ ПК</span>
      <h1>Начните разговор</h1>
      <p>Выберите чат слева или создайте новый личный диалог.</p>
      <button className="primary-button" type="button" onClick={onNewChat}>
        <Plus size={18} /> Новый диалог
      </button>
    </section>
  );
}

function MessageBubble({
  message,
  mine,
  onReply,
  onReaction,
}: {
  message: MessengerMessage;
  mine: boolean;
  onReply: () => void;
  onReaction: () => void;
}) {
  const attachment = message.attachment;
  return (
    <article className={`message-row${mine ? " is-mine" : ""}`}>
      {!mine && <Avatar name={message.sender_name} color={message.sender_color} size="small" />}
      <div className="message-body">
        {!mine && <strong className="message-author">{message.sender_name}</strong>}
        {message.reply_preview && (
          <div className="message-reply">
            <span />
            <p><b>{message.reply_preview.sender_name}</b>{message.reply_preview.body || "Вложение"}</p>
          </div>
        )}
        <div className="message-bubble">
          {attachment && (
            <div className="message-attachment">
              {attachment.mime_type.startsWith("image/") ? <ImagePlus size={20} /> : <FileText size={20} />}
              <span>
                <strong>{attachment.file_name}</strong>
                <small>{Math.max(1, Math.round(attachment.size_bytes / 1024))} КБ</small>
              </span>
            </div>
          )}
          {message.body && <p>{message.body}</p>}
          <time>{time(message.created_at)}{message.edited_at ? " · изм." : ""}</time>
        </div>
        <div className="message-actions">
          <button type="button" onClick={onReaction} title="Нравится">👍</button>
          <button type="button" onClick={onReply} title="Ответить"><MessageCircle size={14} /></button>
        </div>
        {message.reactions && message.reactions.length > 0 && (
          <div className="message-reactions">
            {message.reactions.map((reaction) => (
              <button type="button" key={reaction.emoji} className={reaction.mine ? "is-mine" : ""} onClick={onReaction}>
                {reaction.emoji} <span>{reaction.count}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </article>
  );
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <h2>{title}</h2>
          <IconButton label="Закрыть" onClick={onClose}><X size={18} /></IconButton>
        </header>
        {children}
      </section>
    </div>
  );
}

function SettingsView({
  profile,
  volume,
  callConnected,
  onVolume,
  onBack,
  onSignOut,
}: {
  profile?: UserProfile;
  volume: number;
  callConnected: boolean;
  onVolume: (value: number) => void;
  onBack: () => void;
  onSignOut: () => void;
}) {
  return (
    <section className="settings-view">
      <header className="settings-header">
        <button type="button" className="back-button" onClick={onBack}><X size={18} /> Закрыть настройки</button>
        <span>НАСТРОЙКИ</span>
      </header>
      <div className="settings-scroll">
        <div className="settings-intro">
          <Avatar name={profile?.display_name ?? "Пользователь"} color={profile?.avatar_color} size="large" online />
          <div>
            <span className="eyebrow">ПРОФИЛЬ</span>
            <h1>{profile?.display_name ?? "Пользователь"}</h1>
            <p>{profile?.username ? `@${profile.username}` : profile?.email}</p>
          </div>
        </div>
        <section className="settings-card">
          <div>
            <span className="eyebrow">ЗВУК</span>
            <h2>Голосовой канал</h2>
            <p>Громкость действует на участников текущего голосового канала.</p>
          </div>
          <label className="settings-range">
            <span><Volume2 size={17} /> Громкость <b>{Math.round(volume * 100)}%</b></span>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={volume}
              onChange={(event) => onVolume(Number(event.target.value))}
            />
          </label>
          <p className="settings-status">{callConnected ? "Канал подключён — управление активно." : "Подключитесь к каналу, чтобы проверить микрофон и камеру."}</p>
        </section>
        <section className="settings-card">
          <div>
            <span className="eyebrow">БЕЗОПАСНОСТЬ</span>
            <h2>Сессия на этом ПК</h2>
            <p>В приложении нет ключей VPS или service role. Для звонка выдаётся короткоживущий токен после проверки доступа к чату.</p>
          </div>
          <button type="button" className="danger-outline" onClick={onSignOut}><LogOut size={17} /> Выйти на этом компьютере</button>
        </section>
      </div>
    </section>
  );
}

export default App;

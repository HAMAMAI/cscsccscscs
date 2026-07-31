import { Room, RoomEvent, Track, type RemoteTrack } from "livekit-client";
import type { CallCredentials, CallUiState } from "./types";

const disconnected: CallUiState = {
  connected: false,
  muted: false,
  cameraEnabled: false,
  screenSharing: false,
  participantCount: 0,
};

/**
 * A thin, renderer-only wrapper around LiveKit. It never receives a LiveKit
 * server secret: the token is minted on the VPS after Supabase membership has
 * been checked.
 */
export class DesktopCallClient {
  private room: Room | null = null;
  private audioElements = new Set<HTMLMediaElement>();
  private onStateChange: (state: CallUiState) => void = () => undefined;
  private outputVolume = 1;

  async connect(
    credentials: CallCredentials,
    onStateChange: (state: CallUiState) => void,
  ): Promise<void> {
    this.disconnect();
    this.onStateChange = onStateChange;

    const room = new Room({
      adaptiveStream: true,
      dynacast: true,
    });
    this.room = room;

    const sync = () => this.publishState();
    room.on(RoomEvent.ParticipantConnected, sync);
    room.on(RoomEvent.ParticipantDisconnected, sync);
    room.on(RoomEvent.ConnectionStateChanged, sync);
    room.on(RoomEvent.TrackSubscribed, (track) => {
      this.attachAudio(track as RemoteTrack);
      this.applyOutputVolume();
      sync();
    });
    room.on(RoomEvent.TrackUnsubscribed, (track) => {
      this.detachAudio(track as RemoteTrack);
      sync();
    });
    room.on(RoomEvent.Disconnected, () => {
      if (this.room === room) {
        this.room = null;
        this.clearAudio();
        this.onStateChange(disconnected);
      }
    });

    try {
      await this.withTimeout(
        room.connect(credentials.serverUrl, credentials.token),
        15_000,
        "Не удалось подключиться к голосовому каналу за 15 секунд.",
      );
      // This is called from a user gesture, but an autoplay-policy failure must
      // not eject an otherwise connected participant from the voice room.
      await room.startAudio().catch(() => undefined);
      // A rejected microphone permission must not eject the user from a room.
      // They remain connected and may enable it later from the mute control.
      await room.localParticipant.setMicrophoneEnabled(true).catch(() => undefined);
      this.publishState();
    } catch (error) {
      if (this.room === room) this.room = null;
      this.clearAudio();
      room.disconnect();
      this.onStateChange(disconnected);
      throw error;
    }
  }

  async setMuted(muted: boolean): Promise<void> {
    const room = this.requireRoom();
    await room.localParticipant.setMicrophoneEnabled(!muted);
    this.publishState();
  }

  async setCameraEnabled(enabled: boolean): Promise<void> {
    const room = this.requireRoom();
    await room.localParticipant.setCameraEnabled(enabled);
    this.publishState();
  }

  async setScreenShareEnabled(enabled: boolean): Promise<void> {
    const room = this.requireRoom();
    await room.localParticipant.setScreenShareEnabled(enabled, { audio: true });
    this.publishState();
  }

  setOutputVolume(value: number): void {
    this.outputVolume = Math.min(1, Math.max(0, value));
    this.applyOutputVolume();
  }

  disconnect(): void {
    const room = this.room;
    this.room = null;
    this.clearAudio();
    if (room) room.disconnect();
    this.onStateChange(disconnected);
  }

  private requireRoom(): Room {
    if (!this.room) throw new Error("Сначала подключитесь к голосовому каналу");
    return this.room;
  }

  private async withTimeout<T>(operation: Promise<T>, timeoutMs: number, message: string): Promise<T> {
    let timeout: number | undefined;
    const deadline = new Promise<never>((_, reject) => {
      timeout = window.setTimeout(() => reject(new Error(message)), timeoutMs);
    });
    try {
      return await Promise.race([operation, deadline]);
    } finally {
      if (timeout !== undefined) window.clearTimeout(timeout);
    }
  }

  private attachAudio(track: RemoteTrack): void {
    if (track.kind !== Track.Kind.Audio) return;
    const element = track.attach();
    element.autoplay = true;
    element.volume = this.outputVolume;
    element.dataset.taktRemoteAudio = "true";
    document.body.appendChild(element);
    this.audioElements.add(element);
  }

  private detachAudio(track: RemoteTrack): void {
    if (track.kind !== Track.Kind.Audio) return;
    track.detach().forEach((element) => {
      this.audioElements.delete(element);
      element.remove();
    });
  }

  private clearAudio(): void {
    this.audioElements.forEach((element) => {
      element.pause();
      element.remove();
    });
    this.audioElements.clear();
  }

  private applyOutputVolume(): void {
    this.audioElements.forEach((element) => {
      element.volume = this.outputVolume;
    });
    this.room?.remoteParticipants.forEach((participant) => {
      participant.setVolume(this.outputVolume);
    });
  }

  private publishState(): void {
    const room = this.room;
    if (!room) {
      this.onStateChange(disconnected);
      return;
    }
    this.onStateChange({
      connected: room.state === "connected",
      muted: !room.localParticipant.isMicrophoneEnabled,
      cameraEnabled: room.localParticipant.isCameraEnabled,
      screenSharing: room.localParticipant.isScreenShareEnabled,
      participantCount: room.remoteParticipants.size + 1,
    });
  }
}

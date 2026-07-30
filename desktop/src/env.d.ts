/// <reference types="vite/client" />

interface Window {
  taktDesktop?: {
    isDesktop: boolean;
    platform: string;
    openExternal(url: string): Promise<boolean>;
  };
}

import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Такт — говорите свободно",
  description: "Приватные чаты по ссылке и аудиозвонки без регистрации по email.",
  manifest: "/manifest.webmanifest",
};

export const viewport: Viewport = {
  themeColor: "#100d23",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}

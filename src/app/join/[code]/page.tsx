import { TaktApp } from "@/components/takt-app";

export default async function JoinPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return <TaktApp inviteCode={code.toUpperCase()} />;
}

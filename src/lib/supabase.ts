import { createClient } from "@supabase/supabase-js";

// Publishable values are safe in a browser bundle; environment variables can
// override them for forks and preview environments.
const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "https://qewgunjxdpliyeazyjkn.supabase.co";
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? "sb_publishable_oFE1KGP-BLnRJT_IaJ30Bg_eaFyPaf2";

export const supabase = createClient(supabaseUrl, supabaseKey, {
  auth: { persistSession: false, autoRefreshToken: false },
  realtime: { params: { eventsPerSecond: 20 } },
});

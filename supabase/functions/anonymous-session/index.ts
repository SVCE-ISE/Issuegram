import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  // First try native GoTrue anonymous signup in case the dashboard toggle is enabled
  const anonResp = await fetch(`${supabaseUrl}/auth/v1/signup`, {
    method: "POST",
    headers: {
      "apikey": serviceRoleKey,
      "Content-Type": "application/json",
    },
    body: "{}",
  });

  if (anonResp.ok) {
    const body = await anonResp.text();
    return new Response(body, {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  // Otherwise provision a genuine GoTrue user via Admin API (auto-confirmed, cryptographically random 36-char UUID password)
  const uid = crypto.randomUUID();
  const internalEmail = `anon-${uid}@anon.issuegram.internal`;
  const internalPassword = crypto.randomUUID();

  const { data: created, error: createErr } = await admin.auth.admin.createUser({
    email: internalEmail,
    password: internalPassword,
    email_confirm: true,
    app_metadata: { provider: "anonymous", is_anonymous: true },
    user_metadata: { is_anonymous: true },
  });

  if (createErr || !created.user) {
    return new Response(
      JSON.stringify({
        code: 500,
        error_code: "anonymous_create_failed",
        msg: createErr?.message ?? "Could not create anonymous identity",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }

  // Sign in to obtain a standard GoTrue session (access_token, refresh_token, expires_in, user.id)
  const tokenResp = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: {
      "apikey": serviceRoleKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      email: internalEmail,
      password: internalPassword,
    }),
  });

  const tokenBody = await tokenResp.text();
  return new Response(tokenBody, {
    status: tokenResp.status,
    headers: { "Content-Type": "application/json" },
  });
});

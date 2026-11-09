import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const ALLOWED_BUCKETS = new Set(["avatars", "issue-media"]);
const OBJECT_NAME_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.jpg$/;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  let limit = 25;
  try {
    const body = await req.json();
    if (typeof body?.limit === "number" && body.limit > 0 && body.limit <= 100) {
      limit = body.limit;
    }
  } catch {
    // Empty or non-JSON body is fine
  }

  // Claim due jobs via SECURITY DEFINER RPC in public (restricted to service_role)
  const { data: jobs, error: claimErr } = await admin.rpc("claim_storage_cleanup_jobs", {
    p_limit: limit,
  });

  if (claimErr) {
    return new Response(
      JSON.stringify({ error: "claim_failed", details: claimErr.message }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }

  const results: Array<{ id: number; bucket_id: string; object_name: string; status: string; error?: string }> = [];

  for (const job of jobs ?? []) {
    const { id, bucket_id, object_name, owner_id } = job;

    // 1. Validate bucket & owned object path shape
    if (
      !ALLOWED_BUCKETS.has(bucket_id) ||
      !OBJECT_NAME_RE.test(object_name) ||
      !object_name.toLowerCase().startsWith(`${String(owner_id).toLowerCase()}/`)
    ) {
      await admin.rpc("complete_storage_cleanup_job", {
        p_id: id,
        p_status: "SKIPPED_INVALID",
        p_error: "Object path failed ownership or format validation",
      });
      results.push({ id, bucket_id, object_name, status: "SKIPPED_INVALID" });
      continue;
    }

    // 2. Re-verify active references right before deleting (so retries/races never delete an active file)
    const { data: isReferenced, error: refErr } = await admin.rpc("is_storage_object_referenced", {
      p_bucket_id: bucket_id,
      p_object_name: object_name,
    });

    if (refErr) {
      await admin.rpc("complete_storage_cleanup_job", {
        p_id: id,
        p_status: "PENDING",
        p_error: `Reference check error: ${refErr.message}`,
      });
      results.push({ id, bucket_id, object_name, status: "RETRY", error: refErr.message });
      continue;
    }

    if (isReferenced === true) {
      await admin.rpc("complete_storage_cleanup_job", {
        p_id: id,
        p_status: "SKIPPED_REFERENCED",
        p_error: null,
      });
      results.push({ id, bucket_id, object_name, status: "SKIPPED_REFERENCED" });
      continue;
    }

    // 3. Delete via official Supabase Storage API
    const { error: removeErr } = await admin.storage.from(bucket_id).remove([object_name]);

    if (removeErr) {
      await admin.rpc("complete_storage_cleanup_job", {
        p_id: id,
        p_status: "PENDING",
        p_error: removeErr.message,
      });
      results.push({ id, bucket_id, object_name, status: "RETRY", error: removeErr.message });
    } else {
      await admin.rpc("complete_storage_cleanup_job", {
        p_id: id,
        p_status: "COMPLETED",
        p_error: null,
      });
      results.push({ id, bucket_id, object_name, status: "COMPLETED" });
    }
  }

  return new Response(
    JSON.stringify({ processed: results.length, results }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
});

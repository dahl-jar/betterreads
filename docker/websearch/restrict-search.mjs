import { readFileSync } from "node:fs";

const deny = (reason) => ({ hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason });

let decision;
try {
  const event = JSON.parse(readFileSync(0, "utf8"));
  const allowedDomains = (process.env.WEB_SEARCH_ALLOWED_DOMAINS ?? "").split(",").filter(Boolean);
  const toolInput = { ...event.tool_input, allowed_domains: allowedDomains };
  delete toolInput.blocked_domains;
  decision = allowedDomains.length === 0
    ? deny("no allowed domains")
    : { hookEventName: "PreToolUse", permissionDecision: "allow", updatedInput: toolInput };
} catch {
  process.stderr.write("search blocked: unreadable hook input");
  process.exit(2);
}
process.stdout.write(JSON.stringify({ hookSpecificOutput: decision }));

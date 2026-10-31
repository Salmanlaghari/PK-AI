import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import test from "node:test";

const codePath = fileURLToPath(new URL("../Code.gs", import.meta.url));
const scriptSource = readFileSync(codePath, "utf8");

const uuid = (tail) => `123e4567-e89b-42d3-a456-${tail.padStart(12, "0")}`;

function createScript({ remainingQuota = 100, failEmail = false } = {}) {
  const cacheValues = new Map();
  const properties = new Map();
  const sentEmails = [];
  const output = {
    value: "",
    mimeType: "",
    setMimeType(value) {
      this.mimeType = value;
      return this;
    },
  };

  const context = {
    ALLOWED_REASONS: undefined,
    ContentService: {
      MimeType: { JSON: "application/json" },
      createTextOutput(value) {
        return { ...output, value, setMimeType: output.setMimeType };
      },
    },
    LockService: {
      getScriptLock: () => ({ tryLock: () => true, releaseLock: () => {} }),
    },
    CacheService: {
      getScriptCache: () => ({
        get: (key) => cacheValues.get(key) || null,
        put: (key, value) => cacheValues.set(key, value),
        remove: (key) => cacheValues.delete(key),
      }),
    },
    PropertiesService: {
      getScriptProperties: () => ({
        getProperty: (key) => properties.get(key) || null,
        setProperty: (key, value) => properties.set(key, value),
      }),
    },
    MailApp: {
      getRemainingDailyQuota: () => remainingQuota,
      sendEmail: (to, subject, body) => {
        if (failEmail) throw new Error("simulated mail failure");
        sentEmails.push({ to, subject, body });
      },
    },
    Utilities: {
      DigestAlgorithm: { SHA_256: "SHA_256" },
      Charset: { UTF_8: "UTF_8" },
      computeDigest: (_algorithm, value) => Array.from(createHash("sha256").update(value, "utf8").digest()),
      base64EncodeWebSafe: (bytes) => Buffer.from(bytes).toString("base64url"),
      formatDate: (date) => date.toISOString().slice(0, 10).replaceAll("-", ""),
    },
  };
  vm.createContext(context);
  vm.runInContext(scriptSource, context);
  return {
    call: (payload) => JSON.parse(context.doPost({ postData: { contents: JSON.stringify(payload) } }).value),
    sentEmails,
    cacheValues,
    properties,
  };
}

function validReport(overrides = {}) {
  return {
    version: 1,
    reportId: uuid("1"),
    installationId: uuid("2"),
    reason: "Sexual content",
    detail: "Unexpected response",
    preview: "A bounded preview of the AI output.",
    model: "Test model",
    clientTimestampMs: 1_700_000_000_000,
    appVersion: "1.1.4",
    ...overrides,
  };
}

test("valid report emails the fixed inbox and duplicate request is idempotent", () => {
  const script = createScript();
  const report = validReport();

  assert.deepEqual(script.call(report), { ok: true });
  assert.equal(script.sentEmails.length, 1);
  assert.equal(script.sentEmails[0].to, "lovefaillaghari@gmail.com");
  assert.match(script.sentEmails[0].subject, /^PK-AI AI content report: Sexual content$/);
  assert.match(script.sentEmails[0].body, /Unexpected response/);
  assert.match(script.sentEmails[0].body, /A bounded preview/);
  assert.doesNotMatch(script.sentEmails[0].body, new RegExp(report.installationId));
  assert.deepEqual(script.call(report), { ok: true, duplicate: true });
  assert.equal(script.sentEmails.length, 1);
});

test("rejects invalid reasons, extra fields, malformed IDs, and oversized content", () => {
  const script = createScript();

  assert.deepEqual(script.call(validReport({ reason: "Unapproved" })), { ok: false, code: "INVALID_REPORT" });
  assert.deepEqual(script.call(validReport({ extra: "not allowed" })), { ok: false, code: "INVALID_REPORT" });
  assert.deepEqual(script.call(validReport({ reportId: "not-a-uuid" })), { ok: false, code: "INVALID_REPORT" });
  assert.deepEqual(script.call(validReport({ detail: "d".repeat(1_001) })), { ok: false, code: "INVALID_REPORT" });
  assert.deepEqual(script.call(validReport({ preview: "p".repeat(301) })), { ok: false, code: "INVALID_REPORT" });
  assert.equal(script.sentEmails.length, 0);
});

test("enforces per-install cooldown across different report IDs", () => {
  const script = createScript();
  assert.deepEqual(script.call(validReport()), { ok: true });
  assert.deepEqual(script.call(validReport({ reportId: uuid("3") })), { ok: false, code: "RATE_LIMITED" });
  assert.equal(script.sentEmails.length, 1);
});

test("enforces the 25-email daily cap", () => {
  const script = createScript();
  for (let index = 1; index <= 25; index += 1) {
    const result = script.call(validReport({
      reportId: uuid(String(index + 10)),
      installationId: uuid(String(index + 100)),
    }));
    assert.deepEqual(result, { ok: true });
  }
  assert.deepEqual(script.call(validReport({
    reportId: uuid("900"),
    installationId: uuid("901"),
  })), { ok: false, code: "DAILY_LIMIT" });
  assert.equal(script.sentEmails.length, 25);
});

test("stops when the account email quota is exhausted or MailApp rejects the send", () => {
  const noQuota = createScript({ remainingQuota: 0 });
  assert.deepEqual(noQuota.call(validReport()), { ok: false, code: "EMAIL_QUOTA" });
  assert.equal(noQuota.sentEmails.length, 0);

  const mailFailure = createScript({ failEmail: true });
  assert.deepEqual(mailFailure.call(validReport()), { ok: false, code: "DELIVERY_FAILED" });
  assert.equal(mailFailure.sentEmails.length, 0);
});

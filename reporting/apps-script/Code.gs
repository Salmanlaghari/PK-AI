const SUPPORT_EMAIL = 'lovefaillaghari@gmail.com';
const MAX_REPORT_EMAILS_PER_DAY = 25;
const INSTALLATION_COOLDOWN_SECONDS = 120;
const IDEMPOTENCY_TTL_SECONDS = 21600;
const MAX_REQUEST_CHARS = 8192;
const MAX_DETAIL_CHARS = 1000;
const MAX_PREVIEW_CHARS = 300;
const MAX_MODEL_CHARS = 120;
const ALLOWED_REASONS = [
  'Sexual content',
  'Violent or harmful',
  'Misleading/false',
  'Other',
];
const ALLOWED_FIELDS = [
  'version',
  'reportId',
  'installationId',
  'reason',
  'detail',
  'preview',
  'model',
  'clientTimestampMs',
  'appVersion',
];

/** Public Apps Script web-app endpoint. Deploy with Execute as: Me. */
function doPost(event) {
  const raw = event && event.postData && event.postData.contents;
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > MAX_REQUEST_CHARS) {
    return jsonResponse_({ ok: false, code: 'INVALID_REQUEST' });
  }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_) {
    return jsonResponse_({ ok: false, code: 'INVALID_JSON' });
  }

  const report = validateReport_(parsed);
  if (!report) return jsonResponse_({ ok: false, code: 'INVALID_REPORT' });

  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return jsonResponse_({ ok: false, code: 'BUSY' });

  try {
    const cache = CacheService.getScriptCache();
    const reportKey = 'report:' + digest_(report.reportId);
    if (cache.get(reportKey)) return jsonResponse_({ ok: true, duplicate: true });

    const installationKey = 'install:' + digest_(report.installationId);
    if (cache.get(installationKey)) return jsonResponse_({ ok: false, code: 'RATE_LIMITED' });

    const now = new Date();
    const day = Utilities.formatDate(now, 'Etc/UTC', 'yyyyMMdd');
    const propertyKey = 'sent_count_' + day;
    const properties = PropertiesService.getScriptProperties();
    const previousCount = Number(properties.getProperty(propertyKey) || '0');
    if (!Number.isFinite(previousCount) || previousCount >= MAX_REPORT_EMAILS_PER_DAY) {
      return jsonResponse_({ ok: false, code: 'DAILY_LIMIT' });
    }
    if (MailApp.getRemainingDailyQuota() < 1) {
      return jsonResponse_({ ok: false, code: 'EMAIL_QUOTA' });
    }

    // Reserve before sending so concurrent requests cannot exceed the hard cap.
    properties.setProperty(propertyKey, String(previousCount + 1));
    cache.put(installationKey, '1', INSTALLATION_COOLDOWN_SECONDS);

    try {
      MailApp.sendEmail(
        SUPPORT_EMAIL,
        'PK-AI AI content report: ' + report.reason,
        buildEmailBody_(report, now)
      );
    } catch (_) {
      // Release the reserved daily slot when Apps Script rejects the email.
      properties.setProperty(propertyKey, String(previousCount));
      cache.remove(installationKey);
      return jsonResponse_({ ok: false, code: 'DELIVERY_FAILED' });
    }

    // Do not store report content or the raw per-install ID in script properties.
    cache.put(reportKey, 'sent', IDEMPOTENCY_TTL_SECONDS);
    return jsonResponse_({ ok: true });
  } finally {
    lock.releaseLock();
  }
}

function validateReport_(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null;
  const keys = Object.keys(input);
  if (keys.some(function (key) { return ALLOWED_FIELDS.indexOf(key) === -1; })) return null;
  if (input.version !== 1) return null;
  if (!isUuid_(input.reportId) || !isUuid_(input.installationId)) return null;
  if (typeof input.reason !== 'string' || ALLOWED_REASONS.indexOf(input.reason) === -1) return null;
  if (typeof input.detail !== 'string' || input.detail.length > MAX_DETAIL_CHARS) return null;
  if (typeof input.preview !== 'string' || input.preview.length > MAX_PREVIEW_CHARS || !input.preview.trim()) return null;
  if (typeof input.model !== 'string' || input.model.length > MAX_MODEL_CHARS) return null;
  if (!Number.isSafeInteger(input.clientTimestampMs) || input.clientTimestampMs < 0) return null;
  if (typeof input.appVersion !== 'string' || input.appVersion.length > 30) return null;

  return {
    reportId: input.reportId,
    installationId: input.installationId,
    reason: input.reason,
    detail: input.detail.trim(),
    preview: input.preview,
    model: input.model.trim() || 'Unknown',
    appVersion: input.appVersion,
  };
}

function buildEmailBody_(report, receivedAt) {
  return [
    'PK-AI AI-generated content report',
    'Reason: ' + report.reason,
    'Model: ' + report.model,
    'App version: ' + report.appVersion,
    'Received (UTC): ' + receivedAt.toISOString(),
    'Optional user details: ' + (report.detail || '(none)'),
    '',
    'AI output preview (maximum 300 characters):',
    report.preview,
  ].join('\n');
}

function isUuid_(value) {
  return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function digest_(value) {
  const bytes = Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    value,
    Utilities.Charset.UTF_8
  );
  return Utilities.base64EncodeWebSafe(bytes).replace(/=+$/, '');
}

function jsonResponse_(value) {
  return ContentService.createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}

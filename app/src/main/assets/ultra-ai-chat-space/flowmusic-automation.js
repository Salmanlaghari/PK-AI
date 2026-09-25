// ============================================================================
// Flow Music Automation Engine  (injected into the Flow Music WebView)
// ----------------------------------------------------------------------------
// This script is evaluated INSIDE the real https://flowmusic.app session that
// the user signed into with their own Google account. It lets the Ultra AI 4
// chat screen drive Flow Music without the user ever leaving the chat UI:
//
//   1. probe()    -> reports whether a real Flow Music session exists
//   2. generate() -> types the prompt into Flow Music's studio, clicks Create,
//                    then watches the DOM until a fresh audio result appears
//                    and reports it back to the native layer.
//
// It is intentionally defensive: Flow Music is a Next.js SPA whose DOM can
// change at any time, so every lookup uses multiple heuristics and every
// failure is reported gracefully instead of throwing.
// ============================================================================

(function () {
  var FLOW = (window.__FLOW_AUTOMATION__ = window.__FLOW_AUTOMATION__ || {});

  function native() {
    try {
      return window.FlowMusicNative || null;
    } catch (e) {
      return null;
    }
  }

  function log(msg) {
    var text = "[FlowMusic Automation] " + msg;
    console.log(text);
    try {
      var n = native();
      if (n && typeof n.onAutomationLog === "function") n.onAutomationLog(String(msg));
    } catch (e) {}
  }

  function report(obj) {
    try {
      var n = native();
      if (n && typeof n.onTrackResult === "function") {
        n.onTrackResult(JSON.stringify(obj));
      }
    } catch (e) {
      console.log("[FlowMusic Automation] report failed: " + e);
    }
  }

  function reportProgress(stage, message, extra) {
    var payload = { stage: stage, message: message || "" };
    if (extra) {
      for (var k in extra) {
        if (Object.prototype.hasOwnProperty.call(extra, k)) payload[k] = extra[k];
      }
    }
    try {
      var n = native();
      if (n && typeof n.onProgress === "function") n.onProgress(JSON.stringify(payload));
    } catch (e) {}
    console.log("[FlowMusic Automation][progress] " + stage + " :: " + (message || ""));
  }

  // --------------------------------------------------------------------------
  // Session probe — detect a real signed-in Flow Music (Supabase) session.
  // --------------------------------------------------------------------------
  // Read the Supabase session from wherever Flow Music keeps it.
  // Current Google Flow Music uses @supabase/ssr, which stores the session in
  // CHUNKED BASE64 COOKIES (sb-<ref>-auth-token.0, .1, ...) instead of
  // localStorage. Older builds used localStorage, so we check both.
  function readSession() {
    // 1) localStorage (legacy builds)
    try {
      var keys = Object.keys(localStorage || {});
      for (var i = 0; i < keys.length; i++) {
        var k = keys[i];
        if (/auth-token/i.test(k) || /^sb-.*-auth-token/i.test(k)) {
          var v = localStorage.getItem(k);
          if (v && v.length > 10) {
            try {
              return JSON.parse(v);
            } catch (e) {}
          }
        }
      }
    } catch (e) {}

    // 2) cookies (current Google Flow Music)
    try {
      var jar = document.cookie || "";
      var pairs = jar.split("; ");
      var chunks = {};
      var base = null;
      for (var j = 0; j < pairs.length; j++) {
        var idx = pairs[j].indexOf("=");
        if (idx < 0) continue;
        var cname = pairs[j].slice(0, idx);
        var cval = pairs[j].slice(idx + 1);
        var m = cname.match(/^(sb-.*-auth-token)(?:\.(\d+))?$/);
        if (!m) continue;
        base = m[1];
        var n = m[2] === undefined ? 0 : parseInt(m[2], 10);
        chunks[n] = cval;
      }
      if (base) {
        var joined = "";
        var idxs = Object.keys(chunks)
          .map(Number)
          .sort(function (a, b) {
            return a - b;
          });
        for (var z = 0; z < idxs.length; z++) joined += chunks[idxs[z]];
        if (joined.indexOf("base64-") === 0) joined = joined.slice(7);
        joined = joined.replace(/-/g, "+").replace(/_/g, "/");
        while (joined.length % 4) joined += "=";
        return JSON.parse(atob(joined));
      }
    } catch (e) {}
    return null;
  }

  FLOW.probe = function () {
    var signedIn = false;
    var email = "";
    var name = "";
    try {
      var o = readSession();
      if (o) {
        signedIn = true;
        var u = (o && o.user) || (o && o.currentSession && o.currentSession.user) || null;
        if (u) {
          email = u.email || "";
          name =
            u.user_metadata && (u.user_metadata.full_name || u.user_metadata.name)
              ? u.user_metadata.full_name || u.user_metadata.name
              : "";
        }
      }
    } catch (e) {}

    var hasStudio = false;
    try {
      hasStudio = !!findPromptInput();
    } catch (e) {}

    return JSON.stringify({
      signedIn: signedIn,
      email: email,
      name: name,
      hasStudio: hasStudio,
      url: location.href,
    });
  };

  // --------------------------------------------------------------------------
  // Heuristic DOM helpers
  // --------------------------------------------------------------------------
  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 20 || r.height < 8) return false;
    var st = window.getComputedStyle(el);
    if (st.display === "none" || st.visibility === "hidden" || st.opacity === "0") return false;
    return true;
  }

  function findPromptInput() {
    var els = document.querySelectorAll(
      'textarea, input[type="text"], input[type="search"], [contenteditable="true"], [role="textbox"]'
    );
    var best = null;
    var bestScore = -1;
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (!visible(el)) continue;
      var ph = (
        el.getAttribute("placeholder") ||
        el.getAttribute("aria-label") ||
        el.getAttribute("data-placeholder") ||
        ""
      ).toLowerCase();
      var r = el.getBoundingClientRect();
      var score = 0;
      if (/song|music|prompt|describe|create|make|compose|idea|lyric|instrument|vibe|track/.test(ph))
        score += 12;
      if (el.tagName === "TEXTAREA") score += 4;
      if (el.isContentEditable) score += 3;
      score += Math.min(r.width / 200, 3);
      score += Math.min(r.top / 300, 3);
      if (score > bestScore) {
        bestScore = score;
        best = el;
      }
    }
    return best;
  }

  function setInputValue(el, value) {
    try {
      el.focus();
      if (el.isContentEditable) {
        el.innerHTML = "";
        el.textContent = value;
        el.dispatchEvent(new InputEvent("input", { bubbles: true, data: value }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
        return true;
      }
      var proto =
        el.tagName === "TEXTAREA"
          ? window.HTMLTextAreaElement.prototype
          : window.HTMLInputElement.prototype;
      var desc = Object.getOwnPropertyDescriptor(proto, "value");
      if (desc && desc.set) desc.set.call(el, value);
      else el.value = value;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
      return true;
    } catch (e) {
      log("setInputValue failed: " + e);
      return false;
    }
  }

  function findGenerateButton() {
    var els = document.querySelectorAll(
      'button, [role="button"], a[role="button"], input[type="submit"]'
    );
    var best = null;
    var bestScore = 0;
    for (var i = 0; i < els.length; i++) {
      var b = els[i];
      if (!visible(b)) continue;
      if (b.disabled) continue;
      var label = (
        b.getAttribute("aria-label") ||
        b.getAttribute("title") ||
        ""
      )
        .trim()
        .toLowerCase();
      var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
      if (!label && !txt) continue;
      var score = 0;
      // The real Flow Music send control is an icon button whose aria-label is
      // "Send message". Prefer it strongly over any text suggestion card.
      if (/^(send|send message|send prompt|submit|generate|go)$/.test(label)) score += 40;
      else if (/send/.test(label)) score += 20;
      if (/^(send|submit|generate|create|go)$/.test(txt)) score += 14;
      if (b.getAttribute("type") === "submit") score += 8;
      // Penalise long suggestion cards ("Make songs for fun", ...) and nav
      // items so they can never win over the actual send control.
      if (txt.length > 18) score -= 25;
      if (
        /make songs for fun|create music for videos|make tracks|remix unreleased|make fx|get started|explore|featured|grid|list|upgrade|invite|account|^compose$/.test(
          txt
        )
      )
        score -= 30;
      if (score > bestScore) {
        bestScore = score;
        best = b;
      }
    }
    return best;
  }

  // Real Flow Music streams the finished song from storage as
  //   https://storage.googleapis.com/producer-app-public/clips/<id>.m4a
  // and its player never exposes an <audio> element. We therefore also watch
  // the browser's Resource Timing entries to catch the finished clip.
  function resourceClipUrls() {
    var out = [];
    try {
      var entries = performance.getEntriesByType("resource") || [];
      for (var i = 0; i < entries.length; i++) {
        var n = entries[i].name || "";
        if (/\/clips\/[0-9a-fA-F-]+\.(m4a|mp3|wav|ogg)(\?|$)/.test(n)) out.push(n);
      }
    } catch (e) {}
    return out;
  }

  function audioSources() {
    var set = {};
    var els = document.querySelectorAll(
      'audio, audio source, [data-audio-url], a[href$=".mp3"], a[href$=".wav"], a[href$=".m4a"]'
    );
    for (var i = 0; i < els.length; i++) {
      var u =
        els[i].currentSrc ||
        els[i].src ||
        els[i].getAttribute("href") ||
        els[i].getAttribute("data-audio-url") ||
        els[i].getAttribute("src") ||
        "";
      if (u) set[u] = true;
    }
    var clips = resourceClipUrls();
    for (var k = 0; k < clips.length; k++) set[clips[k]] = true;
    return set;
  }

  function findNewAudio(before) {
    // 1) Real Flow Music: the finished song is streamed from storage.
    var clips = resourceClipUrls();
    for (var c = clips.length - 1; c >= 0; c--) {
      if (!before[clips[c]]) return clips[c];
    }
    // 2) <audio>/<video> elements (legacy / other builds)
    var els = document.querySelectorAll("audio, audio source");
    for (var i = 0; i < els.length; i++) {
      var u = els[i].currentSrc || els[i].src || els[i].getAttribute("src") || "";
      if (u && !before[u] && /^(https?:|blob:)/.test(u)) return u;
    }
    // 3) explicit download links
    var links = document.querySelectorAll(
      'a[href$=".mp3"], a[href$=".wav"], a[href$=".m4a"], a[download]'
    );
    for (var j = 0; j < links.length; j++) {
      var lu = links[j].href || "";
      if (lu && !before[lu] && /^(https?:|blob:)/.test(lu)) return lu;
    }
    return null;
  }

  function guessTitle(prompt) {
    var TIME_RE = /^\d{1,2}:\d{2}(\s*\/\s*\d{1,2}:\d{2})?$/;
    var LABEL_RE = /^(compose|lyrics|sound|details|advanced|instrumental|thoughts|account|upgrade|welcome to the studio|your producer|ask producer|add lyrics|describe the sound|today|yesterday)$/i;
    function clean(s) {
      return (s || "").replace(/\s+/g, " ").trim();
    }
    try {
      // ---- PRIMARY: the song title sits on the same row as the audio player ----
      // Real Flow Music renders the finished clip like:
      //   [ Song Title ]   [ 0:06 / 2:54 ]   [ play button ]
      // The player time node looks like "0:06 / 2:54".
      var all = document.querySelectorAll("div, span, p");
      var player = null;
      for (var i = 0; i < all.length; i++) {
        var txt = clean(all[i].innerText);
        if (all[i].children.length === 0 && /^\d{1,2}:\d{2}\s*\/\s*\d{1,2}:\d{2}$/.test(txt)) {
          player = all[i];
          break;
        }
      }
      if (player) {
        var pr = player.getBoundingClientRect();
        var best = null;
        var bestLeft = -1;
        for (var j = 0; j < all.length; j++) {
          var el = all[j];
          if (el === player || el.contains(player)) continue;
          var t = clean(el.innerText);
          if (!t || t.length < 2 || t.length > 60) continue;
          if (TIME_RE.test(t) || LABEL_RE.test(t)) continue;
          if (/\d{1,2}:\d{2}/.test(t)) continue;
          var r = el.getBoundingClientRect();
          // Same visual row as the player, and positioned to its LEFT.
          if (
            Math.abs(r.top - pr.top) < 28 &&
            r.right <= pr.left + 8 &&
            r.left > pr.left - 400 &&
            r.left > bestLeft &&
            r.width > 0 &&
            r.height > 0
          ) {
            bestLeft = r.left;
            best = t;
          }
        }
        if (best) {
          log("Title detected (player row): '" + best + "'");
          return best;
        }
      }

      // ---- SECONDARY: session header (some builds show the generated title) ----
      var headers = document.querySelectorAll(
        'div[class*="truncate"][class*="text-base"][class*="font-semibold"], span[class*="truncate"][class*="text-base"][class*="font-semibold"], div[class*="truncate"][class*="text-sm"][class*="font-medium"], span[class*="truncate"][class*="text-sm"][class*="font-medium"]'
      );
      var bestTitle = null;
      var bestTop = 1e9;
      for (var h = 0; h < headers.length; h++) {
        var hr = headers[h].getBoundingClientRect();
        var ht = clean(headers[h].innerText);
        if (
          hr.top < 70 &&
          ht &&
          ht.length > 1 &&
          ht.length < 60 &&
          !TIME_RE.test(ht) &&
          !LABEL_RE.test(ht) &&
          !/today|yesterday|\b(am|pm)\b/i.test(ht) &&
          hr.top < bestTop
        ) {
          bestTop = hr.top;
          bestTitle = ht;
        }
      }
      if (bestTitle) {
        log("Title detected (header): '" + bestTitle + "'");
        return bestTitle;
      }

      // ---- TERTIARY: generic headings (other builds) ----
      var nodes = document.querySelectorAll(
        'h1, h2, h3, [class*="title" i], [class*="Title"], [data-testid*="title" i]'
      );
      for (var k = 0; k < nodes.length; k++) {
        var t2 = clean(nodes[k].innerText);
        if (
          t2 &&
          t2.length > 1 &&
          t2.length < 80 &&
          !LABEL_RE.test(t2) &&
          !/flow music|create|generate|sign in|welcome to the studio|your producer|ultra ai|^compose$|^lyrics$|^sound$|^details$|^advanced$|^instrumental$|^add lyrics|^describe the sound|^ask producer|^thoughts$|^account$|^upgrade$/i.test(
            t2
          )
        ) {
          return t2;
        }
      }
    } catch (e) {}
    return prompt;
  }

  // --------------------------------------------------------------------------
  // Generation driver
  // --------------------------------------------------------------------------
  function enterStudio() {
    try {
      var els = document.querySelectorAll('a, button, [role="button"]');
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        if (!visible(el)) continue;
        var t = (
          el.innerText ||
          el.textContent ||
          el.getAttribute("aria-label") ||
          ""
        )
          .trim()
          .toLowerCase();
        if (!t || t.length > 40) continue;
        if (
          /^(create|new song|new track|start creating|get started|studio|make a song|create music|start|create song)$/.test(
            t
          ) ||
          /create (a )?(new )?(song|track|music)|start creating|open studio|new song/.test(t)
        ) {
          log("Entering studio via: '" + t + "'");
          el.click();
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  FLOW.generate = function (prompt) {
    log("Generation requested: " + prompt);
    if (!prompt || !prompt.trim()) {
      report({ ok: false, error: "Empty music prompt." });
      return;
    }

    reportProgress("queued", "Ultra Chat AI se connect ho gaya. Ultra Studio tayyar ho raha hai...");

    var attempts = 0;
    var maxAttempts = 24; // ~12s for the SPA to settle
    function locateAndRun() {
      var input = findPromptInput();
      if (!input) {
        attempts++;
        if (attempts >= maxAttempts) {
          report({
            ok: false,
            error:
              "Ultra Studio is not ready. Sign in to Ultra Chat AI first, then try again.",
          });
          return;
        }
        // Every few attempts, try to navigate into the studio from the landing page.
        if (attempts % 4 === 0) enterStudio();
        reportProgress("waiting_studio", "Ultra Studio load ho raha hai... (" + attempts + ")");
        setTimeout(locateAndRun, 500);
        return;
      }
      runGeneration(input, prompt);
    }
    locateAndRun();
  };

  function runGeneration(input, prompt) {
    var before = audioSources();
    if (!setInputValue(input, prompt)) {
      report({ ok: false, error: "Could not write the prompt into Ultra Studio." });
      return;
    }
    log("Prompt written into Ultra Studio input.");
    reportProgress("prompt_entered", "Prompt Ultra Studio mein likh diya gaya hai.");

    setTimeout(function () {
      var btn = findGenerateButton();
      if (!btn) {
        report({
          ok: false,
          error: "Could not find the Create/Generate button on Ultra Studio.",
        });
        return;
      }
      log(
        "Clicking send button: '" +
          ((btn.innerText || "").trim() || btn.getAttribute("aria-label") || "") +
          "'"
      );
      try {
        btn.click();
      } catch (e) {
        report({ ok: false, error: "Failed to click the Ultra AI 4 generate button." });
        return;
      }
      reportProgress("generating", "Ultra AI 4 track compose kar raha hai...");

      var started = Date.now();
      var lastTick = 0;
      var timer = setInterval(function () {
        var url = findNewAudio(before);
        if (url) {
          clearInterval(timer);
          log("Track detected: " + url);
          reportProgress("finalizing", "Track mil gaya, finalize ho raha hai...");
          report({
            ok: true,
            audioUrl: url,
            title: guessTitle(prompt),
            prompt: prompt,
          });
          return;
        }
        var elapsed = Math.round((Date.now() - started) / 1000);
        if (elapsed - lastTick >= 6) {
          lastTick = elapsed;
          reportProgress("generating", "Ultra AI 4 track compose kar raha hai... (" + elapsed + "s)");
        }
        if (Date.now() - started > 360000) {
          clearInterval(timer);
          report({
            ok: false,
            error: "Ultra AI 4 generation timed out after 6 minutes.",
          });
        }
      }, 2500);
    }, 1600);
  }

  FLOW.ready = true;
  return "flowmusic-automation-ready";
})();

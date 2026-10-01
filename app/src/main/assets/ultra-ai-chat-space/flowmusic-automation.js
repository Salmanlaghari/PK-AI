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

  // Correlation ids for the single in-flight requests. The native side has
  // one result channel per flow, so a second overlapping call would clobber
  // the first prompt and steal its reply — hence the re-entrancy guards in
  // FLOW.generate / FLOW.chat.
  var activeTrackRequestId = null;
  var activeChatRequestId = null;

  function report(obj) {
    try {
      // Correlate the result with the track request that produced it.
      if (activeTrackRequestId) obj.requestId = activeTrackRequestId;
      activeTrackRequestId = null;
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

  /** Progress for the in-flight chat request (carries its correlation id). */
  function reportChatProgress(stage, message) {
    reportProgress(stage, message, { requestId: activeChatRequestId });
  }

  /** Progress for the in-flight track request (carries its correlation id). */
  function reportTrackProgress(stage, message) {
    reportProgress(stage, message, { requestId: activeTrackRequestId });
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
  // The clip id is not always hex, so match any non-empty path segment.
  function isClipUrl(n) {
    return /\/clips\/[^?#\s'"]+\.(m4a|mp3|wav|ogg|webm)(\?|#|$)/i.test(n || "");
  }

  function resourceClipUrls() {
    var out = [];
    try {
      var entries = performance.getEntriesByType("resource") || [];
      for (var i = 0; i < entries.length; i++) {
        var n = entries[i].name || "";
        if (isClipUrl(n)) out.push(n);
      }
    } catch (e) {}
    return out;
  }

  // Last-resort clip hunt: scan the raw page HTML for a clip URL. Used when
  // the finished-song UI rendered but no resource/audio URL was captured
  // (e.g. the clip was fetched before our observer attached).
  function deepClipSearch(before) {
    try {
      var html = document.documentElement.innerHTML || "";
      var re = /https?:\/\/[^"'<>\s]*\/clips\/[^"'<>\s]*\.(m4a|mp3|wav|ogg|webm)(\?[^"'<>\s]*)?/gi;
      var m;
      while ((m = re.exec(html)) !== null) {
        if (!before[m[0]]) return m[0];
      }
    } catch (e) {}
    return null;
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

  FLOW.generate = function (requestId, prompt) {
    // Single-flight: a second generation would clobber the first prompt and
    // steal its result on the single native channel.
    if (activeTrackRequestId !== null) {
      try {
        var n0 = native();
        if (n0 && typeof n0.onTrackResult === "function") {
          n0.onTrackResult(
            JSON.stringify({
              ok: false,
              error: "Ek track pehle se ban raha hai. Pehle uska intezar karein.",
              requestId: requestId,
            })
          );
        }
      } catch (e) {}
      return;
    }
    activeTrackRequestId = requestId;
    log("Generation requested (id=" + requestId + ").");
    if (!prompt || !prompt.trim()) {
      report({ ok: false, error: "Empty music prompt." });
      return;
    }
    // The chat and track flows share one WebView composer: a chat typed
    // mid-generation would clobber the studio prompt (and vice versa), so
    // fail fast with a clear message instead of corrupting both.
    if (activeChatRequestId !== null) {
      report({ ok: false, error: "Pehle chat ka jawab aanay dein, phir song banayein." });
      return;
    }

    reportTrackProgress("queued", "Ultra Chat AI se connect ho gaya. Ultra Studio tayyar ho raha hai...");

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
        reportTrackProgress("waiting_studio", "Ultra Studio load ho raha hai... (" + attempts + ")");
        setTimeout(locateAndRun, 500);
        return;
      }
      runGeneration(input, prompt);
    }
    locateAndRun();
  };

  function runGeneration(input, prompt) {
    // The backend WebView lives for the whole app session, so its
    // resource-timing buffer (default 250 entries) may already be full and
    // would silently drop the finished clip. Clear it and watch new entries
    // live with a PerformanceObserver instead of trusting the buffer alone.
    try {
      performance.clearResourceTimings();
    } catch (e) {}
    var before = audioSources();
    var observedClips = [];
    var clipObserver = null;
    try {
      clipObserver = new PerformanceObserver(function (list) {
        var entries = list.getEntries() || [];
        for (var i = 0; i < entries.length; i++) {
          var n = entries[i].name || "";
          if (isClipUrl(n) && observedClips.indexOf(n) < 0) {
            observedClips.push(n);
            log("Observed clip resource: " + n);
          }
        }
      });
      clipObserver.observe({ type: "resource", buffered: false });
    } catch (e) {
      clipObserver = null;
    }
    function stopObserving() {
      try {
        if (clipObserver) clipObserver.disconnect();
      } catch (e) {}
      clipObserver = null;
    }
    if (!setInputValue(input, prompt)) {
      stopObserving();
      report({ ok: false, error: "Could not write the prompt into Ultra Studio." });
      return;
    }
    log("Prompt written into Ultra Studio input.");
    reportTrackProgress("prompt_entered", "Prompt Ultra Studio mein likh diya gaya hai.");

    setTimeout(function () {
      var btn = findGenerateButton();
      if (!btn) {
        stopObserving();
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
        stopObserving();
        report({ ok: false, error: "Failed to click the Ultra AI 4 generate button." });
        return;
      }
      reportTrackProgress("generating", "Ultra AI 4 track compose kar raha hai...");

      var started = Date.now();
      var lastTick = 0;
      var deepScanDone = false;
      var timer = setInterval(function () {
        var url = findNewAudio(before);
        // Live observer catch: entries the (possibly full) timing buffer missed.
        if (!url) {
          for (var i = 0; i < observedClips.length; i++) {
            if (!before[observedClips[i]]) {
              url = observedClips[i];
              break;
            }
          }
        }
        // Last resort: after 90s with no URL, scan the raw HTML once — the
        // finished player UI may be rendered without a captured resource.
        if (!url && !deepScanDone && Date.now() - started > 90000) {
          deepScanDone = true;
          url = deepClipSearch(before);
          if (url) log("Track detected via deep scan: " + url);
        }
        if (url) {
          clearInterval(timer);
          stopObserving();
          log("Track detected: " + url);
          reportTrackProgress("finalizing", "Track mil gaya, finalize ho raha hai...");
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
          reportTrackProgress("generating", "Ultra AI 4 track compose kar raha hai... (" + elapsed + "s)");
        }
        if (Date.now() - started > 360000) {
          clearInterval(timer);
          stopObserving();
          report({
            ok: false,
            error: "Ultra AI 4 generation timed out after 6 minutes.",
          });
        }
      }, 2500);
    }, 1600);
  }

  // --------------------------------------------------------------------------
  // AI chat driver — REAL text answers from the Flow Music AI
  // --------------------------------------------------------------------------
  // Types the prompt into the Flow Music AI composer, sends it, then watches
  // the DOM until the assistant's reply settles and reports the text back.
  //
  // Selectors verified against the live site (Next.js custom chat UI):
  //   input: textarea[aria-label="Chat message"]  (placeholder "Describe your song...")
  //   send:  button[aria-label="Send message"]
  // Assistant message containers are NOT in the logged-out DOM, so reply
  // reading uses defensive heuristics: known assistant markers first, then
  // "new visible text that was not there before we sent".
  function reportChat(obj) {
    try {
      // Correlate the result with the chat request that produced it, then
      // release the single-flight guard.
      obj.requestId = activeChatRequestId;
      activeChatRequestId = null;
      var n = native();
      if (n && typeof n.onChatResult === "function") {
        n.onChatResult(JSON.stringify(obj));
      }
    } catch (e) {
      console.log("[FlowMusic Automation] chat report failed: " + e);
    }
  }

  function findChatInput() {
    // 1) Verified selector from the live site.
    try {
      var v = document.querySelector('textarea[aria-label="Chat message"]');
      if (v && visible(v)) return v;
    } catch (e) {}
    // 2) Heuristic fallback for future DOM changes.
    var els = document.querySelectorAll('textarea, [contenteditable="true"], [role="textbox"]');
    var best = null;
    var bestScore = -1;
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (!visible(el)) continue;
      var ph = (
        (el.getAttribute("placeholder") || "") +
        " " +
        (el.getAttribute("aria-label") || "")
      ).toLowerCase();
      var score = 0;
      if (/chat message/.test(ph)) score += 15;
      if (/describe your song/.test(ph)) score += 10;
      if (/\bask\b|\bmessage\b/.test(ph)) score += 8;
      if (el.tagName === "TEXTAREA") score += 4;
      if (score > bestScore) {
        bestScore = score;
        best = el;
      }
    }
    return bestScore > 0 ? best : null;
  }

  function findChatSendButton() {
    // 1) Verified selector from the live site.
    try {
      var v = document.querySelector('button[aria-label="Send message"]');
      if (v && visible(v) && !v.disabled) return v;
    } catch (e) {}
    // 2) No generic fallback: the shared music-studio heuristic resolves to
    // the song GENERATE button, and clicking it would start a real song
    // generation (spending credits) for a chat prompt. Failing loudly here
    // is strictly better than submitting to the wrong control.
    return null;
  }

  function isGeneratingNow() {
    try {
      var stop = document.querySelector(
        'button[aria-label="Stop"], button[aria-label="Stop generating"], button[aria-label="Cancel"]'
      );
      if (stop && visible(stop)) return true;
    } catch (e) {}
    return false;
  }

  // Known assistant-message markers used by chat UIs (checked first).
  function markedAssistantTexts() {
    var out = [];
    var sels = [
      '[data-message-author-role="assistant"]',
      '[data-role="assistant"]',
      '[data-testid*="assistant" i]',
      '[class*="assistant-message" i]',
    ];
    for (var s = 0; s < sels.length; s++) {
      var els;
      try {
        els = document.querySelectorAll(sels[s]);
      } catch (e) {
        continue;
      }
      for (var i = 0; i < els.length; i++) {
        if (!visible(els[i])) continue;
        var t = (els[i].innerText || "").replace(/\s+/g, " ").trim();
        if (t.length >= 2) out.push(t);
      }
    }
    return out;
  }

  // Fallback: every visible leaf text block on the page.
  function leafTexts() {
    var out = [];
    var els = document.querySelectorAll("div, p, li, span, td");
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.children.length !== 0) continue;
      if (!visible(el)) continue;
      var t = (el.innerText || "").replace(/\s+/g, " ").trim();
      if (t.length < 8 || t.length > 12000) continue;
      out.push(t);
    }
    return out;
  }

  function snapshotTexts() {
    // Null-prototype object: a visible text block equal to "constructor",
    // "toString", etc. must not be mistaken for "already seen".
    var set = Object.create(null);
    var marked = markedAssistantTexts();
    var leafs = leafTexts();
    for (var i = 0; i < marked.length; i++) set[marked[i]] = true;
    for (var j = 0; j < leafs.length; j++) set[leafs[j]] = true;
    return set;
  }

  // Longest visible text that was NOT on screen before we sent the prompt.
  function findNewReplyText(before, promptText) {
    var best = "";
    function consider(t) {
      if (!t || t.length < 2) return;
      if (before[t]) return;
      // Never mistake our own echoed prompt (or a prefix of the reply that
      // merely quotes it) for the answer.
      if (promptText && (t === promptText || t.indexOf(promptText) === 0)) return;
      if (t.length > best.length) best = t;
    }
    var marked = markedAssistantTexts();
    for (var i = 0; i < marked.length; i++) consider(marked[i]);
    // Only fall back to generic leaf blocks when no marked assistant node exists.
    if (marked.length === 0) {
      var leafs = leafTexts();
      for (var j = 0; j < leafs.length; j++) consider(leafs[j]);
    }
    return best;
  }

  FLOW.chat = function (requestId, prompt) {
    // Single-flight: a second chat would type into the same composer
    // (clobbering the first prompt) and steal its reply on the single native
    // channel. The in-flight request is untouched; the new caller fails fast.
    if (activeChatRequestId !== null) {
      try {
        var n0 = native();
        if (n0 && typeof n0.onChatResult === "function") {
          n0.onChatResult(
            JSON.stringify({
              ok: false,
              error: "Ek jawab pehle se tayyar ho raha hai. Pehle uska intezar karein.",
              requestId: requestId,
            })
          );
        }
      } catch (e) {}
      return;
    }
    activeChatRequestId = requestId;
    log("Chat requested (id=" + requestId + ").");
    if (!prompt || !prompt.trim()) {
      reportChat({ ok: false, error: "Empty prompt." });
      return;
    }
    // The chat and track flows share one WebView composer: typing a chat
    // prompt mid-generation would clobber the studio prompt, so fail fast
    // with a clear message instead of corrupting both.
    if (activeTrackRequestId !== null) {
      reportChat({ ok: false, error: "Ek track pehle se ban raha hai. Pehle uska intezar karein." });
      return;
    }

    reportChatProgress("queued", "Ultra AI 4 se connect ho gaya. Jawab tayyar ho raha hai...");

    var attempts = 0;
    var maxAttempts = 24; // ~12s for the SPA to settle
    function locateAndRun() {
      var input = findChatInput();
      if (!input) {
        attempts++;
        if (attempts >= maxAttempts) {
          reportChat({
            ok: false,
            error: "Ultra AI 4 chat is not ready. Sign in to Ultra Chat AI first, then try again.",
          });
          return;
        }
        reportChatProgress("waiting_chat", "Ultra AI 4 chat load ho raha hai... (" + attempts + ")");
        setTimeout(locateAndRun, 500);
        return;
      }
      runChat(input, prompt);
    }
    locateAndRun();
  };

  function runChat(input, prompt) {
    var before = snapshotTexts();
    if (!setInputValue(input, prompt.trim())) {
      reportChat({ ok: false, error: "Could not write into the Ultra AI 4 chat box." });
      return;
    }
    log("Prompt written into chat input.");
    reportChatProgress("prompt_entered", "Sawal Ultra AI 4 ko bhej diya gaya hai.");

    setTimeout(function () {
      var btn = findChatSendButton();
      if (!btn) {
        reportChat({ ok: false, error: "Could not find the Send button on Ultra AI 4 chat." });
        return;
      }
      try {
        btn.click();
      } catch (e) {
        reportChat({ ok: false, error: "Failed to send the message to Ultra AI 4." });
        return;
      }
      // Some builds submit on Enter instead of the button; if the textarea
      // still holds our text after the click, press Enter as a fallback.
      setTimeout(function () {
        try {
          var stillThere = (input.value || input.textContent || "").trim();
          if (stillThere && stillThere.indexOf(prompt.trim().slice(0, 24)) !== -1) {
            // Synthetic key events are untrusted; some frameworks ignore
            // keydown alone, so send the full keydown/keypress/keyup trio.
            var init = { key: "Enter", code: "Enter", bubbles: true, cancelable: true };
            input.dispatchEvent(new KeyboardEvent("keydown", init));
            input.dispatchEvent(new KeyboardEvent("keypress", init));
            input.dispatchEvent(new KeyboardEvent("keyup", init));
          }
        } catch (e) {}
      }, 1200);

      reportChatProgress("thinking", "Ultra AI 4 jawab soch raha hai...");
      watchChatReply(before, prompt.trim());
    }, 1400);
  }

  function watchChatReply(before, promptText) {
    var started = Date.now();
    var settled = false;
    var lastText = "";
    var stablePolls = 0;
    var lastTick = 0;
    var announcedReplying = false;
    var sawGenerating = false;

    var timer = setInterval(function () {
      if (settled) return;
      var current = findNewReplyText(before, promptText);
      var generating = isGeneratingNow();
      if (generating) sawGenerating = true;

      if (current && !announcedReplying) {
        announcedReplying = true;
        reportChatProgress("replying", "Ultra AI 4 jawab likh raha hai...");
      }

      if (current === lastText && current) {
        stablePolls++;
      } else {
        stablePolls = 0;
        lastText = current;
      }

      // Reply is done when the text stops growing for ~3s (2 polls) and the
      // model is no longer generating — or, more reliably, when the stop
      // button was visible and has now disappeared (stream finished), which
      // does not depend on the reply text being perfectly stable.
      if (
        lastText &&
        ((stablePolls >= 2 && !generating) || (sawGenerating && !generating))
      ) {
        settled = true;
        clearInterval(timer);
        log("Chat reply captured (" + lastText.length + " chars).");
        reportChat({ ok: true, text: lastText });
        return;
      }

      var elapsed = Math.round((Date.now() - started) / 1000);
      if (elapsed - lastTick >= 8) {
        lastTick = elapsed;
        reportChatProgress(
          generating || announcedReplying ? "replying" : "thinking",
          "Ultra AI 4 jawab tayyar kar raha hai... (" + elapsed + "s)"
        );
      }
      if (Date.now() - started > 240000) {
        settled = true;
        clearInterval(timer);
        // If we captured a partial reply, still deliver it rather than failing.
        if (lastText) {
          log("Chat reply timed out; delivering partial text.");
          reportChat({ ok: true, text: lastText, partial: true });
        } else {
          reportChat({ ok: false, error: "Ultra AI 4 se jawab nahi mil saka (timeout)." });
        }
      }
    }, 1500);
  }

  FLOW.ready = true;
  return "flowmusic-automation-ready";
})();

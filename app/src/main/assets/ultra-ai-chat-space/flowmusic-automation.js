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
  FLOW.probe = function () {
    var signedIn = false;
    var email = "";
    var name = "";
    try {
      var keys = Object.keys(localStorage || {});
      for (var i = 0; i < keys.length; i++) {
        var k = keys[i];
        if (/auth-token/i.test(k) || /^sb-.*-auth-token/i.test(k)) {
          var v = localStorage.getItem(k);
          if (v && v.length > 10) {
            signedIn = true;
            try {
              var o = JSON.parse(v);
              var u = (o && o.user) || (o && o.currentSession && o.currentSession.user) || null;
              if (u) {
                email = u.email || "";
                name =
                  u.user_metadata && (u.user_metadata.full_name || u.user_metadata.name)
                    ? u.user_metadata.full_name || u.user_metadata.name
                    : "";
              }
            } catch (e) {}
            break;
          }
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
    var els = document.querySelectorAll('button, [role="button"], a[role="button"]');
    var best = null;
    var bestScore = 0;
    for (var i = 0; i < els.length; i++) {
      var b = els[i];
      if (!visible(b)) continue;
      if (b.disabled) continue;
      var txt = (
        b.innerText ||
        b.textContent ||
        b.getAttribute("aria-label") ||
        b.getAttribute("title") ||
        ""
      )
        .trim()
        .toLowerCase();
      if (!txt) continue;
      var score = 0;
      if (/^(create|generate|make|compose|start|submit|build|go)\b/.test(txt)) score += 12;
      if (/song|music|track|create|generate|compose/.test(txt)) score += 6;
      if (b.getAttribute("type") === "submit") score += 4;
      if (score > bestScore) {
        bestScore = score;
        best = b;
      }
    }
    return best;
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
    return set;
  }

  function findNewAudio(before) {
    var els = document.querySelectorAll("audio, audio source");
    for (var i = 0; i < els.length; i++) {
      var u = els[i].currentSrc || els[i].src || els[i].getAttribute("src") || "";
      if (u && !before[u] && /^(https?:|blob:)/.test(u)) return u;
    }
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
    try {
      var nodes = document.querySelectorAll(
        'h1, h2, h3, [class*="title" i], [class*="Title"], [data-testid*="title" i]'
      );
      for (var i = 0; i < nodes.length; i++) {
        var t = (nodes[i].innerText || "").trim();
        if (t && t.length > 1 && t.length < 80 && !/flow music|create|generate|sign in/i.test(t)) {
          return t;
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
      log("Clicking generate button: '" + (btn.innerText || "").trim() + "'");
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
        if (Date.now() - started > 240000) {
          clearInterval(timer);
          report({
            ok: false,
            error: "Ultra AI 4 generation timed out after 4 minutes.",
          });
        }
      }, 2500);
    }, 1600);
  }

  FLOW.ready = true;
  return "flowmusic-automation-ready";
})();

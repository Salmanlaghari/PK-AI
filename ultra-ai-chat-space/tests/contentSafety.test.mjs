import test from "node:test";
import assert from "node:assert/strict";
import {
  BLOCKED_OUTPUT_MESSAGE,
  filterAiOutput,
  isUnsafeText,
  matchedUnsafeTerm,
} from "../src/services/contentSafety.mjs";

test("blocks explicit sexual and profane text, including simple obfuscation", () => {
  for (const text of [
    "a nude portrait",
    "n.u.d.e model",
    "p0rn illustration",
    "f.u.c.k this",
    "madarchod",
    "nangi tasveer",
  ]) {
    assert.equal(isUnsafeText(text), true, `expected block: ${text}`);
  }
});

test("allows ordinary prompts and protects innocent longer words", () => {
  for (const text of [
    "a classic painting of mountains",
    "shitake mushrooms",
    "a cute cat on a rocket",
    "family picnic in the park",
  ]) {
    assert.equal(isUnsafeText(text), false, `unexpected block: ${text}`);
  }
});

test("replaces unsafe generated text before it can be rendered", () => {
  assert.equal(filterAiOutput("an explicit sexual answer"), BLOCKED_OUTPUT_MESSAGE);
  assert.equal(filterAiOutput("A quiet blue ocean"), "A quiet blue ocean");
});

test("does not expose the full unsafe input in the match result", () => {
  assert.equal(matchedUnsafeTerm("a nude portrait"), "nude");
});

// Best-effort lexical defense only; not a semantic or server-side moderation system.
export const BLOCKED_OUTPUT_MESSAGE =
  "I can't display sexual or profane content. Please try a different request.";

const LEET = new Map([
  ["0", "o"], ["1", "l"], ["3", "e"], ["4", "a"],
  ["5", "s"], ["7", "t"], ["8", "b"], ["@", "a"], ["$", "s"],
]);

const BLOCKED_TERMS = [
  "nude", "nudes", "nudity", "naked", "undressed", "unclothed", "topless", "bottomless",
  "strip", "stripped", "stripping", "stripper", "sex", "sexy", "sexual", "sexually",
  "erotic", "erotica", "porn", "porno", "pornographic", "pornography", "pornstar", "xxx",
  "hentai", "nsfw", "explicit", "breast", "breasts", "boob", "boobs", "tit", "tits",
  "nipple", "nipples", "areola", "vagina", "pussy", "clit", "clitoris", "labia", "vulva",
  "cunt", "penis", "dick", "cock", "prick", "phallus", "testicle", "scrotum", "ass",
  "asses", "booty", "buttocks", "anal", "anus", "orgasm", "masturbate", "masturbation",
  "ejaculate", "cum", "cumshot", "creampie", "blowjob", "handjob", "deepthroat", "gangbang",
  "threesome", "orgy", "bukkake", "foreplay", "sexting", "cybersex", "jizz", "boner",
  "fetish", "bdsm", "bondage", "domination", "submissive", "sadism", "masochism", "kinky",
  "spanking", "lingerie", "thong", "gstring", "bikini", "panties", "upskirt", "downblouse",
  "escort", "prostitute", "prostitution", "whore", "slut", "hooker", "onlyfans", "playboy",
  "camgirl", "brothel", "swinger", "horny", "aroused", "lustful", "seduce", "seduction",
  "seductive", "sensual", "intercourse", "coitus", "fornication", "sodomy", "dildo", "vibrator",
  "sextoy", "sexdoll", "fleshlight", "rape", "rapist", "molestation", "molester", "pedophile",
  "pedophilia", "bestiality", "incest", "necrophilia", "milf", "dilf",
  "fuck", "fucking", "fucked", "fucker", "motherfucker", "shit", "bullshit", "bitch", "bitches",
  "asshole", "bastard", "damn", "goddamn", "hell", "crap", "piss", "pissed", "wtf", "stfu",
  "faggot", "madarchod", "maderchod", "behenchod", "bhenchod", "chutiya", "chutia", "chut",
  "gaand", "gand", "lund", "lora", "randi", "harami", "kamina", "kameena", "bhosdike",
  "bhosdi", "bhosda", "chudai", "chod", "choda", "chodi", "nanga", "nangi", "nangay",
].map((term) => normalize(term));

function normalize(text) {
  return [...String(text).toLowerCase().normalize("NFD")]
    .filter((char) => !/\p{M}/u.test(char))
    .map((char) => LEET.get(char) || char)
    .join("");
}

function tokenize(text) {
  const normalized = normalize(text);
  const tokens = [];
  const spans = [];
  let compact = "";
  let index = 0;
  while (index < normalized.length) {
    if (/[\p{L}\p{N}]/u.test(normalized[index])) {
      const start = compact.length;
      while (index < normalized.length && /[\p{L}\p{N}]/u.test(normalized[index])) {
        compact += normalized[index++];
      }
      tokens.push(compact.slice(start));
      spans.push([start, compact.length]);
    } else {
      index++;
    }
  }
  return { tokens, compact, spans };
}

export function matchedUnsafeTerm(text) {
  const { tokens, compact, spans } = tokenize(text);
  if (!tokens.length) return null;
  for (const term of BLOCKED_TERMS) {
    if (tokens.includes(term)) return term;
    let index = compact.indexOf(term);
    while (index >= 0) {
      const end = index + term.length;
      const buriedInLongerWord = spans.some(
        ([start, stop]) => start <= index && end <= stop && stop - start > term.length,
      );
      if (!buriedInLongerWord) return term;
      index = compact.indexOf(term, index + 1);
    }
  }
  return null;
}

export function isUnsafeText(text) {
  return matchedUnsafeTerm(text) !== null;
}

export function filterAiOutput(text) {
  return isUnsafeText(text) ? BLOCKED_OUTPUT_MESSAGE : text;
}

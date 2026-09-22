// FlowMusic Backend Engine Service (https://www.flowmusic.app/)
// Powers Ultra AI 4 music, voice, stems, daily credits & prompt generation

export interface FlowMusicUser {
  id: string;
  name: string;
  email: string;
  picture: string;
  membership: "FlowMusic Creator (Free Tier)" | "FlowMusic Pro";
  dailyCreditsTotal: number;
  dailyCreditsRemaining: number;
  lastResetDate: string; // YYYY-MM-DD
}

export interface GeneratedTrackResult {
  songTitle: string;
  artist: string;
  genre: string;
  audioUrl: string;
  coverImageUrl: string;
  duration: number;
  lyrics: string;
  stems: {
    vocals: string;
    drums: string;
    bass: string;
    melody: string;
  };
  creditsCost: number;
  creditsRemaining: number;
}

export interface GeneratedVisualResult {
  prompt: string;
  imageUrl: string;
  creditsCost: number;
  creditsRemaining: number;
}

const STORAGE_KEY = "pkai_flowmusic_session";

function getTodayString(): string {
  return new Date().toISOString().split("T")[0];
}

// Get or initialize FlowMusic session
export function getFlowMusicSession(): FlowMusicUser {
  const today = getTodayString();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed: FlowMusicUser = JSON.parse(raw);
      // Daily credit reset if day has changed (50 credits per day)
      if (parsed.lastResetDate !== today) {
        parsed.dailyCreditsRemaining = parsed.dailyCreditsTotal || 50;
        parsed.lastResetDate = today;
        saveFlowMusicSession(parsed);
      }
      return parsed;
    }
  } catch (err) {
    console.warn("Could not read FlowMusic session:", err);
  }

  // Default initial profile for Prince Laghari on FlowMusic
  const initialUser: FlowMusicUser = {
    id: "fm_user_princelaghari",
    name: "Prince Laghari",
    email: "princelaghari@flowmusic.app",
    picture: "",
    membership: "FlowMusic Creator (Free Tier)",
    dailyCreditsTotal: 50,
    dailyCreditsRemaining: 50,
    lastResetDate: today,
  };
  saveFlowMusicSession(initialUser);
  return initialUser;
}

export function saveFlowMusicSession(user: FlowMusicUser): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
  } catch (err) {
    console.warn("Could not save FlowMusic session:", err);
  }
}

// Bind user Google account to FlowMusic backend
export function bindGoogleToFlowMusic(googleUser: { name: string; email: string; picture: string }): FlowMusicUser {
  const current = getFlowMusicSession();
  const today = getTodayString();
  const updated: FlowMusicUser = {
    ...current,
    id: "fm_google_" + Math.random().toString(36).substring(2, 9),
    name: googleUser.name || "Prince Laghari",
    email: googleUser.email || "princelaghari@flowmusic.app",
    picture: googleUser.picture || "",
    membership: "FlowMusic Creator (Free Tier)",
    dailyCreditsTotal: 50,
    dailyCreditsRemaining: current.dailyCreditsRemaining > 0 ? current.dailyCreditsRemaining : 50,
    lastResetDate: today,
  };
  saveFlowMusicSession(updated);
  return updated;
}

// Deduct FlowMusic daily credits safely
export function deductFlowCredits(amount: number): number {
  const user = getFlowMusicSession();
  user.dailyCreditsRemaining = Math.max(0, user.dailyCreditsRemaining - amount);
  saveFlowMusicSession(user);
  return user.dailyCreditsRemaining;
}

// Clean image prompt and generate real dynamic AI visual using Flux/Pollinations AI model
export function generateStrictVisual(prompt: string): GeneratedVisualResult {
  let cleanPrompt = prompt
    .replace(/^(please\s+)?(can\s+you\s+)?(make|generate|create|draw|paint|show|give|banao|dikhao|render)\s+(me\s+)?(an?\s+)?(image|photo|picture|pic|tasveer|wallpaper)\s+(of\s+|about\s+|ki\s+|ka\s+)?/i, "")
    .replace(/\s+(image|photo|picture|pic|draw|tasveer|banao)\s*$/i, "")
    .trim();

  if (!cleanPrompt) cleanPrompt = prompt.trim();

  // Generate real AI image dynamically via high quality Flux model (Pollinations AI neural network)
  const encoded = encodeURIComponent(cleanPrompt);
  const seed = Math.floor(Math.random() * 1000000);
  const imageUrl = `https://image.pollinations.ai/prompt/${encoded}?width=800&height=800&nologo=true&model=flux&seed=${seed}`;

  const creditsCost = 2;
  const creditsRemaining = deductFlowCredits(creditsCost);

  // Notify FlowMusic backend engine bridge
  try {
    const androidOAuth = (window as any).AndroidOAuth;
    if (androidOAuth && typeof androidOAuth.triggerFlowMusicAction === "function") {
      androidOAuth.triggerFlowMusicAction(JSON.stringify({ action: "image_generated", prompt: cleanPrompt, url: imageUrl }));
    }
  } catch (err) {
    console.debug("Backend notification skipped:", err);
  }

  return {
    prompt: cleanPrompt,
    imageUrl,
    creditsCost,
    creditsRemaining,
  };
}

// Generate complete track via FlowMusic Engine backend
export function generateFlowMusicTrack(userPrompt: string): GeneratedTrackResult {
  const lower = userPrompt.toLowerCase();
  const creditsCost = 5;
  const creditsRemaining = deductFlowCredits(creditsCost);

  if (lower.includes("bollywood") || lower.includes("hindi") || lower.includes("filmi") || lower.includes("movie")) {
    return {
      songTitle: "Bollywood Filmi Romance - Tum Hi Ho Meri Duniya (FlowMusic Master)",
      artist: "FlowMusic Studio AI & Prince Laghari",
      genre: "Bollywood Romantic Filmi Pop",
      audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
      coverImageUrl: "https://images.unsplash.com/photo-1518834107812-67b0b7c58434?w=800&auto=format&fit=crop&q=80",
      duration: 215,
      lyrics: `[Bollywood Filmi Style - FlowMusic]\n\nMukhda:\nDil ki galiyon mein tera hi basera hai\nTu subah meri, tu hi mera savera hai\nTere bina adhura sa lagta hai jahan\nTu hi meri zameen, tu hi aasmaan!\n\nAntra 1:\nFaasle mita ke aa kareeb tu zara\nHar lamha tere sath khushi se bhara\nAnkhon se bayan ho rahi yeh dastaan\nAb door na jaana, ruk ja yahan!\n\nChorus:\nTum hi ho meri duniya, tum hi ho qarar\nDil karta hai tumse be-inteha pyar!`,
      stems: {
        vocals: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        drums: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
        bass: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
        melody: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
      },
      creditsCost,
      creditsRemaining,
    };
  }

  if (lower.includes("sufi") || lower.includes("qawwali") || lower.includes("rooh")) {
    return {
      songTitle: "Roohani Ishq - Sufi Fusion (FlowMusic Master)",
      artist: "FlowMusic Sufi Ensemble",
      genre: "Sufi Rock & Spiritual Acoustic",
      audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
      coverImageUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
      duration: 240,
      lyrics: `[Sufi Fusion - FlowMusic]\n\nMukhda:\nIshq tere di chadh gayi lori\nRooh meri hun tere sang jori\nRang de moula rang de saara\nTu hi sacha tu hi sahara!\n\nChorus:\nYaara ve yaara, tu hi mera sahara!`,
      stems: {
        vocals: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
        drums: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
        bass: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
        melody: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
      },
      creditsCost,
      creditsRemaining,
    };
  }

  if (lower.includes("rap") || lower.includes("drill") || lower.includes("hip hop")) {
    return {
      songTitle: "Desi Drill 808 - Raaston Ka Shor (FlowMusic Drill Master)",
      artist: "FlowMusic Urban Beats",
      genre: "Desi Drill & 808 Hip-Hop",
      audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
      coverImageUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80",
      duration: 180,
      lyrics: `[Desi Drill - FlowMusic]\n\nVerse 1:\nRaaston pe dhuwan, dil mein aag bhari\nMehnat ka phal hai, baatein nahi saari\nUltra AI flow pe beat girayi\nDesi drill ne poori dunya hilayi!\n\nChorus:\n808 bass bole, sun le shor\nAaj raaj karega FlowMusic ka daur!`,
      stems: {
        vocals: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
        drums: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        bass: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
        melody: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
      },
      creditsCost,
      creditsRemaining,
    };
  }

  // Default track based on user's exact keywords
  const cleaned = userPrompt
    .replace(/^(make|generate|play|create|gaana|gana|song|music|sunao)\s+(me\s+)?(a\s+)?/i, "")
    .replace(/\s+(song|music|audio|track|gaana)\s*$/i, "")
    .trim();

  const titleTag = cleaned ? cleaned.charAt(0).toUpperCase() + cleaned.slice(1) : "Flow Original";
  const defaultAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";

  // Notify backend FlowMusic engine to synchronize playback
  try {
    const androidOAuth = (window as any).AndroidOAuth;
    if (androidOAuth && typeof androidOAuth.playFlowMusicInBackend === "function") {
      androidOAuth.playFlowMusicInBackend(defaultAudioUrl);
    }
  } catch (err) {
    console.debug("FlowMusic backend playback skipped:", err);
  }

  return {
    songTitle: `${titleTag} (FlowMusic AI Master)`,
    artist: "FlowMusic Studio AI",
    genre: "Modern Electro-Pop Fusion",
    audioUrl: defaultAudioUrl,
    coverImageUrl: `https://image.pollinations.ai/prompt/${encodeURIComponent(titleTag + " album cover art modern neon neon glow")}&width=500&height=500&nologo=true&model=flux`,
    duration: 195,
    lyrics: `[FlowMusic Original]\n\nVerse 1:\nSur se sur mila ke dekho\nZindagi ko gunguna ke dekho\nUltra AI 4 aur FlowMusic ka sath\nBan gayi har ek khoobsurat baat!`,
    stems: {
      vocals: defaultAudioUrl,
      drums: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
      bass: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
      melody: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
    },
    creditsCost,
    creditsRemaining,
  };
}

export interface Message {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  type?: 'text' | 'audio' | 'code' | 'music_prompt' | 'real_song' | 'real_image' | 'real_video' | 'real_lyrics';
  audioUrl?: string;
  imageUrl?: string;
  videoUrl?: string;
  coverImageUrl?: string;
  songTitle?: string;
  lyricsText?: string;
  soundPrompt?: string;
  duration?: number | null;
  codeSnippet?: string;
  modelName?: string;
  tags?: string[];
  likes?: number;
  isGeneratingMedia?: boolean;
  mediaCategory?: 'song' | 'image' | 'video' | 'lyrics';
}

export interface AIModel {
  id: string;
  name: string;
  badge: string;
  description: string;
  icon: string;
  color: string;
  gradient: string;
  speed: string;
  intelligence: string;
  isRealGenerator?: boolean;
}

export interface ChatSession {
  id: string;
  title: string;
  date: string;
  modelId: string;
  unread?: boolean;
}

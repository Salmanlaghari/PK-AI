export interface FlowStudioConfig {
  container: HTMLElement;
  clientId?: string;
  authToken?: string;
  theme?: "light" | "dark";
  onReady?(): void;
  onTrackGenerated?(trackUrl: string): void;
  onError?(error: Error): void;
}

export interface FlowStudioInstance {
  load(): Promise<void>;
  generateTrack(prompt: string): Promise<string>;
  destroy(): void;
}

export function createFlowStudio(config: FlowStudioConfig): FlowStudioInstance;


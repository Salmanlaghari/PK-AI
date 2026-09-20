import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function noModulePlugin() {
  return {
    name: 'no-module-transform',
    transformIndexHtml(html: string) {
      return html
        .replace(/<script type="module" crossorigin/g, '<script defer')
        .replace(/crossorigin/g, '')
    }
  }
}

export default defineConfig({
  base: './',
  plugins: [
    react(),
    tailwindcss(),
    noModulePlugin()
  ],
  resolve: {
    alias: {
      '@flowmusic/sdk': path.resolve(__dirname, 'src/types/flowmusic-mock.ts')
    }
  }
})

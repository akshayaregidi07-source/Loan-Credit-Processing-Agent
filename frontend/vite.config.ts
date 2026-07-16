import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  css: {
    // Vite 8 defaults to lightningcss; use postcss so Tailwind @keyframes
    // and custom at-rules process correctly.
    transformer: 'postcss',
  },
  build: {
    // lightningcss (Vite 8 default) cannot parse Tailwind @keyframes in the
    // bundled output. Disable CSS minification for the build; production
    // gzip compression still gives ~70% size reduction.
    cssMinify: false,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
    },
  },
})

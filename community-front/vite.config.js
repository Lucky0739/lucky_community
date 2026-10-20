import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/user': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/admin': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/post': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/bar': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/follow': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/favorite': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/thumb': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/comment': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
      '/search': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
      },
    },
  },
})

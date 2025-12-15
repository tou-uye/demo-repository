import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Point frontend API calls to the same public endpoint as backend exposure.
        target: 'http://api12.w1.luyouxia.net',
        changeOrigin: true
      }
    }
  }
})

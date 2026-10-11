import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // auth-service only allows CORS from http://localhost:5173, so don't silently pick another port
  server: { port: 5173, strictPort: true },
})

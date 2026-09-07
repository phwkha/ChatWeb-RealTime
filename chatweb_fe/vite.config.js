import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath } from 'node:url'

const envDir = fileURLToPath(new URL('..', import.meta.url))

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, envDir, '')
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  return {
    envDir,
    plugins: [react()],
    define: { global: 'globalThis' },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          configure(proxy) {
            proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'))
          },
        },
        '/ws': { target: proxyTarget, changeOrigin: true, ws: true },
        '/oauth2': { target: proxyTarget, changeOrigin: true },
      },
    },
  }
})

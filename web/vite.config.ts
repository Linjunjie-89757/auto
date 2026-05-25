import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

function resolveProxyTarget(env: Record<string, string>) {
  if (env.VITE_API_PROXY_TARGET) {
    return env.VITE_API_PROXY_TARGET
  }
  if (env.VITE_API_BASE_URL) {
    try {
      const url = new URL(env.VITE_API_BASE_URL)
      return url.origin
    }
    catch {
      // Fall through to default when the env var is not a valid absolute URL.
    }
  }
  return 'http://127.0.0.1:8080'
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    server: {
      port: 4173,
      strictPort: true,
      proxy: {
        '/api': {
          target: resolveProxyTarget(env),
          changeOrigin: true,
        },
      },
    },
  }
})

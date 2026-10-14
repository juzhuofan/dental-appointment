import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: { '/api': { target: environment.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } },
    },
    preview: { host: '127.0.0.1', port: 4173 },
  };
});

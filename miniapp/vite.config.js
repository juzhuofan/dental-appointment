import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

// 使用 uni-app 官方模板的兼容构建链。
export default defineConfig({ plugins: [uni()], server: { host: '127.0.0.1', port: 5174, strictPort: true } });

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [project, command] = process.argv.slice(2);
const commands = {
  'admin-web': {
    dev: ['node_modules/vite/bin/vite.js'],
    build: ['node_modules/vite/bin/vite.js', 'build'],
  },
  miniapp: {
    'dev:mp-weixin': ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', '-p', 'mp-weixin'],
    'build:mp-weixin': ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', 'build', '-p', 'mp-weixin'],
    'dev:h5': ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js'],
    'build:h5': ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', 'build'],
  },
};
const args = commands[project]?.[command];
if (!args) throw new Error(`不支持的项目命令：${project || ''} ${command || ''}`);
const projectRoot = join(root, project);
function readEnv(path) {
  const values = {};
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const match = trimmed.match(/^([A-Z][A-Z0-9_]*)\s*=\s*(.*)$/);
    if (!match || Object.hasOwn(values, match[1])) throw new Error(`${path} 中存在无效或重复配置`);
    values[match[1]] = match[2].trim();
  }
  return values;
}
const selectorPath = join(projectRoot, '.env');
const active = readEnv(selectorPath).APP_ENV;
if (!['development', 'production'].includes(active)) {
  throw new Error(`${selectorPath} 的 APP_ENV 只能是 development 或 production`);
}
const config = readEnv(join(projectRoot, `.env.${active}`));
if (!/^https?:\/\//.test(config.VITE_API_BASE_URL || '') && !config.VITE_API_BASE_URL?.startsWith('/')) {
  throw new Error(`${project} 的 ${active} VITE_API_BASE_URL 无效`);
}
const env = {
  ...process.env,
  VITE_API_BASE_URL: config.VITE_API_BASE_URL,
  VITE_DEV_PROXY_TARGET: config.VITE_DEV_PROXY_TARGET || '',
};
console.log(`[environment] ${project}: ${active}; API: ${config.VITE_API_BASE_URL}`);
const child = spawnSync(process.execPath, [join(projectRoot, args[0]), ...args.slice(1), '--mode', active], {
  cwd: projectRoot,
  env,
  stdio: 'inherit',
  windowsHide: true,
});
if (child.error) throw child.error;
process.exitCode = child.status ?? 1;

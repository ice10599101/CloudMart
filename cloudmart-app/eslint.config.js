// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
const nodeGlobals = require('globals').node;

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*', '.expo/*'],
  },
  {
    // scripts/ 下为 Node 构建脚本（CommonJS），声明 Node 运行时全局变量
    files: ['scripts/**/*.cjs'],
    languageOptions: {
      globals: nodeGlobals,
    },
  },
]);

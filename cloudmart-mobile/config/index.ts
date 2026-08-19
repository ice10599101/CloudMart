import { defineConfig, type UserConfigExport } from '@tarojs/cli'
import type { IH5Config, IMiniAppConfig } from '@tarojs/taro/types/compile/config'
import devConfig from './dev'
import prodConfig from './prod'
import path from 'path'
import fs from 'fs'

/**
 * Taro 4.2.1 的 vite 编译器类型（IMiniAppConfig<'vite'> / IH5Config<'vite'>）
 * 遗漏了 outputRoot 声明，但运行时支持且为双端产物目录约定所必需，
 * 本地交叉类型补齐，待 Taro 修复类型后可移除。
 */
type MiniConfig = IMiniAppConfig<'vite'> & { outputRoot?: string }
type H5Config = IH5Config<'vite'> & { outputRoot?: string }

/**
 * 读取项目根目录的 .env 文件，提取指定变量。
 * 和微服务共用同一份 .env，所有机器通用。
 */
function readRootEnv(key: string): string | undefined {
  const envPath = path.resolve(__dirname, '..', '..', '.env')
  if (!fs.existsSync(envPath)) return undefined
  const content = fs.readFileSync(envPath, 'utf-8')
  const match = content.match(new RegExp(`^${key}=(.+)$`, 'm'))
  return match?.[1]?.trim()
}

/**
 * API 地址优先级：
 * 1. cloudmart-mobile/.env 中的 TARO_APP_API_HOST
 * 2. 项目根目录 .env 中的 EXPO_PUBLIC_API_HOST（和 APP 共用）
 * 3. 兜底 127.0.0.1
 */
const API_HOST = process.env.TARO_APP_API_HOST
  || readRootEnv('EXPO_PUBLIC_API_HOST')
  || 'http://127.0.0.1'

export default defineConfig<'vite'>(async (merge) => {
  const baseConfig: UserConfigExport<'vite'> = {
    projectName: 'cloudmart-mobile',
    date: '2026-6-4',
    designWidth: 750,
    deviceRatio: {
      640: 2.34 / 2,
      750: 1,
      375: 2,
      828: 1.81 / 2,
    },
    sourceRoot: 'src',
    outputRoot: 'dist',
    plugins: [],
    // H5 和小程序使用不同输出目录，避免互相覆盖
    // H5 -> dist/h5, 小程序 -> dist/weapp
    defineConstants: {
      'process.env.TARO_APP_API_HOST': `"${API_HOST}"`,
    },
    copy: {
      patterns: [
        { from: 'src/assets/', to: 'assets/', ignore: [] },
      ],
      options: {},
    },
    framework: 'react',
    compiler: {
      type: 'vite',
      vitePlugins: [
        {
          name: 'exclude-tiptap',
          enforce: 'pre',
          resolveId(source) {
            // 仅小程序构建时把 TiptapEditor 及其依赖重定向到空模块
            // H5 构建保留真实依赖，避免破坏 TiptapEditor 功能
            if (process.env.TARO_ENV === 'weapp') {
              const emptyPath = path.resolve(__dirname, '..', 'src', 'components', 'empty.tsx')
              // 拦截 @tiptap 和 prosemirror 模块
              if (source.includes('@tiptap/') || source.includes('prosemirror-')) {
                return emptyPath
              }
              // 拦截 TiptapEditor 组件入口（别名和直接路径）
              if (source === '@/components/TiptapEditor'
                || source === '@/components/TiptapEditor/index'
                || /[/\\]components[/\\]TiptapEditor[/\\]?$/.test(source)
                || /[/\\]components[/\\]TiptapEditor[/\\]index$/.test(source)) {
                return emptyPath
              }
              // 拦截 TiptapEditor 的扩展文件（相对路径 ./extensions/xxx）
              if (/[/\\]TiptapEditor[/\\]extensions[/\\]/.test(source)) {
                return emptyPath
              }
            }
            return null
          },
        },
      ],
    },
    alias: {
      '@': path.resolve(__dirname, '..', 'src'),
    },
    sass: {
      data: `@import "@/styles/variables.scss";`,
    },
    mini: {
      outputRoot: 'dist/weapp',
      alias: {
        '@': path.resolve(__dirname, '..', 'src'),
      },
      miniCssExtractPluginOption: {
        ignoreOrder: true,
      },
      postcss: {
        pxtransform: {
          enable: true,
          config: {},
        },
        cssModules: {
          enable: true,
          config: {
            namingPattern: 'module',
            generateScopedName: '[name]__[local]___[hash:base64:5]',
          },
        },
      },
    } as MiniConfig,
    h5: {
      outputRoot: 'dist-h5',
      // 禁止 Taro 在编译前清空 outputPath（基于顶层 outputRoot='dist'），
      // 避免运行 dev:h5/build:h5 时误删 dist/weapp 下的小程序产物
      output: {
        clean: false,
      },
      publicPath: '/',
      staticDirectory: 'static',
      miniCssExtractPluginOption: {
        ignoreOrder: true,
        filename: 'css/[name].[hash].css',
        chunkFilename: 'css/[name].[chunkhash].css',
      },
      postcss: {
        autoprefixer: {
          enable: true,
          config: {},
        },
        cssModules: {
          enable: true,
          config: {
            namingPattern: 'module',
            generateScopedName: '[name]__[local]___[hash:base64:5]',
          },
        },
      },
    } as H5Config,
  }

  process.env.BROWSERSLIST_ENV = process.env.NODE_ENV

  if (process.env.NODE_ENV === 'development') {
    return merge({}, baseConfig, devConfig)
  }
  return merge({}, baseConfig, prodConfig)
})

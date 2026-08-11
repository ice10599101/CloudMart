import { defineConfig, type UserConfigExport } from '@tarojs/cli'
import devConfig from './dev'
import prodConfig from './prod'
import path from 'path'
import fs from 'fs'

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
    compiler: 'vite',
    alias: {
      '@': path.resolve(__dirname, '..', 'src'),
    },
    sass: {
      data: `@import "@/styles/variables.scss";`,
    },
    mini: {
      outputRoot: 'dist/weapp',
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
    },
    h5: {
      outputRoot: 'dist/h5',
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
    },
  }

  process.env.BROWSERSLIST_ENV = process.env.NODE_ENV

  if (process.env.NODE_ENV === 'development') {
    return merge({}, baseConfig, devConfig)
  }
  return merge({}, baseConfig, prodConfig)
})

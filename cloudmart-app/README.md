# CloudMart App（Expo / React Native）

CloudMart 三端客户端中的原生 App 端，基于 [Expo](https://docs.expo.dev/) 与
[Expo Router](https://docs.expo.dev/router/introduction/) 文件路由。

## 启动

1. 安装依赖（使用锁文件，保证依赖一致）

   ```bash
   npm ci
   ```

2. 启动开发服务

   ```bash
   npx expo start
   ```

3. 按目标平台运行

   ```bash
   npm run android
   npm run ios
   npm run web
   ```

## 验证与构建

类型检查、单测、Web 导出与生产构建的完整命令见仓库根目录
[docs/BUILD.md](../docs/BUILD.md)，与 CI（`.github/workflows/ci.yml`）口径一致。

常用命令：

```bash
npx tsc --noEmit   # 类型检查
npm test           # Vitest 单测
npx expo export --platform web   # Web 生产导出
```

## Learn more

- [Expo documentation](https://docs.expo.dev/): Learn fundamentals, or go into advanced topics with our [guides](https://docs.expo.dev/guides).
- [Learn Expo tutorial](https://docs.expo.dev/tutorial/introduction/): Follow a step-by-step tutorial where you'll create a project that runs on Android, iOS, and the web.

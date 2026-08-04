# Sentinel 熔断降级规则模板

## 使用方法

1. 登录 Nacos 控制台：`http://129.204.152.168:9090`
2. 配置管理 → 配置列表 → 创建配置
3. 填写配置信息：
   - **Data ID**：`{服务名}-degrade-rules`（如 `mall-auth-degrade-rules`）
   - **Group**：`SENTINEL_GROUP`
   - **格式**：JSON
   - **内容**：复制对应 JSON 文件的内容
4. 发布配置

## 规则字段说明

| 字段 | 说明 | 取值 |
|---|---|---|
| `resource` | 资源名（接口路径） | 如 `POST:/orders` |
| `grade` | 熔断策略 | `0`=慢调用RT, `1`=异常比例, `2`=异常数 |
| `count` | 阈值 | grade=0: 毫秒; grade=1: 0.0~1.0; grade=2: 异常次数 |
| `timeWindow` | 熔断恢复时间（秒） | 如 `10` 表示熔断 10 秒后半开 |
| `minRequestAmount` | 最小请求数 | 统计窗口内请求数不足此值时不熔断 |
| `statIntervalMs` | 统计时间窗口（毫秒） | 如 `10000` 表示 10 秒统计一次 |

## 各服务对应的 Data ID

| 服务 | Data ID | JSON 文件 |
|---|---|---|
| mall-auth | `mall-auth-degrade-rules` | mall-auth-degrade-rules.json |
| mall-user | `mall-user-degrade-rules` | mall-user-degrade-rules.json |
| mall-order | `mall-order-degrade-rules` | mall-order-degrade-rules.json |
| mall-payment | `mall-payment-degrade-rules` | mall-payment-degrade-rules.json |
| mall-seckill | `mall-seckill-degrade-rules` | mall-seckill-degrade-rules.json |

## 熔断策略建议

| 场景 | grade | count | timeWindow | 说明 |
|---|---|---|---|---|
| 支付/认证 | 1 | 0.3 | 15s | 异常比例 30% 即熔断，恢复慢 |
| 订单/秒杀 | 1 | 0.5 | 10s | 异常比例 50% 熔断 |
| 查询接口 | 0 | 2000ms | 10s | RT 超过 2 秒熔断 |
| AI 接口 | 1 | 0.6 | 10s | LLM 不稳定，阈值放宽 |

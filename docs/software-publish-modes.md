# 软件发布权限

每个会员默认禁止发布，管理员可在「会员管理 → 发布资格」或「软件 → 发布资格」设置：

| publishMode | 页面名称 | 提交行为 |
| --- | --- | --- |
| DENIED | 禁止发布 | 不允许创建、保存、提交或重新上架；已有内容保留 |
| DIRECT | 可直接发布 | 保存当前内容后直接上线，返回 APPROVED |
| REVIEW | 发布后需审核 | 保存后进入 PENDING，管理员审核通过后才公开 |

直接发布仍执行内容完整性、账号状态、自动文本检查、全站发布开关等校验；省去人工审核。
权限在提交事务中锁行重查，页面提示不作为授权依据。变更模式不会自动处理已在待审队列中的内容。

## 数据兼容与部署

- `V21__publisher_review_mode.sql` 增加 `review_required`，默认值为 1。既有已开通会员保持需审核；没有资格记录的会员继续默认禁止。
- 发布者申请通过后授予 REVIEW，管理员可再修改为 DIRECT。
- 重启更新后的 `mall-software`，Flyway 会自动执行新增迁移；随后更新前端。`mall-notification` 也需更新，使直接发布通知显示“软件发布成功”。
- Web、App、小程序的发布按钮和结果提示均按新模式显示。

## 接口

`GET /api/software/publisher-status` 保留 `canPublish` 并新增 `publishMode`。

管理员变更权限继续使用 `POST /api/admin/software/publisher-permissions/{userId}/toggle`：

```json
{ "publishMode": "DIRECT", "reason": "授予直接发布权限" }
```

必须携带原有管理员权限及幂等请求键。旧 `{ "enable": true }` 请求继续映射 REVIEW，`false` 映射 DENIED；同时提供的 `enable` 和 `publishMode` 必须一致。
权限审计同时保存变更前后的发布模式，直接发布的内容审计记录 `USER / DIRECT_PUBLISH`。

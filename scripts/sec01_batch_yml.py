# -*- coding: utf-8 -*-
"""SEC-01: append cloudmart.security config block to each module's application.yml."""
import io, os

ROOT = r"D:\Ide\IdeaProjects\CloudMart"

BASE = """# SEC-01 统一身份边界：用户/管理员 JWT 本地验签（RS256/JWKS），服务间调用以
# X-Service-Token 短期签名令牌认证（与全站共享 CLOUDMART_SERVICE_TOKEN_SECRET）。
# 裸 X-Internal-Call 头不再构成任何信任凭证。
cloudmart:
  security:
    service-id: {sid}
    service-token-secret: ${{CLOUDMART_SERVICE_TOKEN_SECRET:}}
    jwks-uri: ${{CLOUDMART_SECURITY_JWKS_URI:http://127.0.0.1:9001/oauth2/jwks}}
    clock-skew-seconds: 30
    service-token-ttl-seconds: 60
"""

def block(sid, paths=None, outbound=None, extra=""):
    s = BASE.format(sid=sid)
    if paths:
        s += "    service-token-paths:\n"
        for prefix, issuers, scope in paths:
            s += "      - prefix: %s\n        issuers: [%s]\n        scope: %s\n" % (
                prefix, ", ".join(issuers), scope)
    if outbound:
        s += "    outbound-scopes:\n"
        for target, scope in outbound:
            s += "      %s: %s\n" % (target, scope)
    if extra:
        s += extra
    return s

CONFIGS = {
    "mall-product": block("mall-product",
        paths=[("/admin", ["mall-admin"], "product:admin"),
               ("/products", ["mall-ai", "mall-cart", "mall-seckill"], "product:read")]),
    "mall-user": block("mall-user",
        paths=[("/admin/users", ["mall-admin", "mall-notification"], "user:admin"),
               ("/admin", ["mall-admin"], "user:admin")],
        outbound=[("mall-community", "community:internal")]),
    "mall-order": block("mall-order",
        paths=[("/orders/timeout-cancel", ["mall-job"], "order:jobs"),
               ("/admin", ["mall-admin"], "order:admin"),
               ("/internal", ["mall-payment"], "order:internal")],
        outbound=[("mall-inventory", "inventory:trade"),
                  ("mall-coupon", "coupon:trade"),
                  ("mall-cart", "cart:trade"),
                  ("mall-payment", "payment:trade")]),
    "mall-payment": block("mall-payment",
        paths=[("/admin", ["mall-admin"], "payment:admin"),
               ("/payments", ["mall-order"], "payment:trade"),
               ("/internal", ["mall-job"], "payment:jobs")],
        outbound=[("mall-order", "order:internal")]),
    "mall-inventory": block("mall-inventory",
        paths=[("/admin", ["mall-admin"], "inventory:admin"),
               ("/internal", ["mall-order"], "inventory:trade")]),
    "mall-cart": block("mall-cart",
        paths=[("/admin", ["mall-admin"], "cart:admin"),
               ("/checked", ["mall-order"], "cart:trade"),
               ("/internal", ["mall-order"], "cart:trade")]),
    "mall-coupon": block("mall-coupon",
        paths=[("/admin", ["mall-admin"], "coupon:admin"),
               ("/coupons/expire-batch", ["mall-job"], "coupon:jobs"),
               ("/user-coupons", ["mall-order"], "coupon:trade"),
               ("/internal", ["mall-order"], "coupon:trade")]),
    "mall-seckill": block("mall-seckill",
        paths=[("/admin", ["mall-admin"], "seckill:admin"),
               ("/execute", ["mall-live"], "seckill:execute"),
               ("/internal", ["mall-live", "mall-order", "mall-job"], "seckill:internal")],
        outbound=[("mall-product", "product:read")]),
    "mall-marketing": block("mall-marketing",
        paths=[("/admin", ["mall-admin"], "marketing:admin"),
               ("/marketing/group/expiration", ["mall-job"], "marketing:jobs"),
               ("/internal", ["mall-job"], "marketing:jobs")]),
    "mall-wms": block("mall-wms",
        paths=[("/admin", ["mall-admin"], "wms:admin"),
               ("/internal", ["mall-job"], "wms:jobs")]),
    "mall-community": block("mall-community",
        paths=[("/admin", ["mall-admin"], "community:admin"),
               ("/internal", ["mall-user", "mall-job"], "community:internal")]),
    "mall-notification": block("mall-notification",
        paths=[("/admin", ["mall-admin"], "notification:admin"),
               ("/internal", ["mall-job", "mall-pet"], "notification:internal")],
        outbound=[("mall-user", "user:admin")]),
    "mall-live": block("mall-live",
        paths=[("/admin", ["mall-admin"], "live:admin"),
               ("/internal", ["mall-job", "mall-wish"], "live:internal")],
        outbound=[("mall-seckill", "seckill:execute")]),
    "mall-ai": block("mall-ai",
        paths=[("/admin", ["mall-admin"], "ai:admin"),
               ("/chat", ["mall-admin"], "ai:admin"),
               ("/search", ["mall-admin"], "ai:admin"),
               ("/internal", ["mall-job"], "ai:internal")],
        outbound=[("mall-product", "product:read")]),
    "mall-risk": block("mall-risk",
        paths=[("/admin", ["mall-admin"], "risk:admin"),
               ("/blacklist", ["mall-admin"], "risk:admin"),
               ("/records", ["mall-admin"], "risk:admin"),
               ("/rules", ["mall-admin"], "risk:admin"),
               ("/internal", ["mall-auth", "mall-order", "mall-payment", "mall-seckill",
                              "mall-user", "mall-job"], "risk:internal")]),
    "mall-auth": block("mall-auth",
        outbound=[("mall-admin", "admin:auth")],
        extra="    user-jwt-verification-enabled: false\n"),
    "mall-admin": block("mall-admin",
        paths=[("/auth", ["mall-auth"], "admin:auth"),
               ("/logs", ["mall-auth"], "admin:auth")],
        outbound=[("mall-product", "product:admin"),
                  ("mall-user", "user:admin"),
                  ("mall-order", "order:admin"),
                  ("mall-inventory", "inventory:admin"),
                  ("mall-payment", "payment:admin"),
                  ("mall-coupon", "coupon:admin"),
                  ("mall-seckill", "seckill:admin"),
                  ("mall-marketing", "marketing:admin"),
                  ("mall-wms", "wms:admin"),
                  ("mall-community", "community:admin"),
                  ("mall-notification", "notification:admin"),
                  ("mall-live", "live:admin"),
                  ("mall-cart", "cart:admin"),
                  ("mall-risk", "risk:admin"),
                  ("mall-ai", "ai:admin"),
                  ("mall-job", "job:admin"),
                  ("mall-gen", "gen:admin")],
        extra="    user-jwt-verification-enabled: false\n"
              "    register-admin-permission-interceptor: false\n"),
    "mall-job": block("mall-job",
        paths=[("/internal", ["mall-admin"], "job:internal")]),
    "mall-gen": block("mall-gen",
        paths=[("/internal", ["mall-admin"], "gen:internal")]),
    "mall-file": block("mall-file",
        paths=[("/internal", ["mall-job", "mall-community"], "file:internal")]),
}

for module, content in CONFIGS.items():
    path = os.path.join(ROOT, module, "src", "main", "resources", "application.yml")
    src = io.open(path, encoding="utf-8").read()
    if "cloudmart:" in src and "security:" in src:
        print("SKIP (already configured):", module)
        continue
    if not src.endswith("\n"):
        src += "\n"
    src += "\n" + content
    io.open(path, "w", encoding="utf-8", newline="\n").write(src)
    print("OK", module)
print("done")

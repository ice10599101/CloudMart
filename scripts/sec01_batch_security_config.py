# -*- coding: utf-8 -*-
"""SEC-01 batch: replace InternalCallAuthenticationFilter with common security filters
in the 15 business modules' SecurityConfig, then delete the old filters."""
import io, os, re

MODULES = ["ai", "cart", "community", "coupon", "inventory", "live", "marketing",
           "notification", "order", "payment", "product", "risk", "seckill", "user", "wms"]
ROOT = r"D:\Ide\IdeaProjects\CloudMart"

IMPORTS_ANCHOR = "import org.springframework.boot.web.servlet.FilterRegistrationBean;"
IMPORTS_NEW = ("import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;\n"
               "import com.cloudmart.common.security.UserJwtAuthenticationFilter;\n"
               "import org.springframework.boot.web.servlet.FilterRegistrationBean;")

FIELD_OLD = "    private final InternalCallAuthenticationFilter internalCallAuthenticationFilter;"
FIELD_NEW = ("    private final UserJwtAuthenticationFilter userJwtAuthenticationFilter;\n"
             "    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;")

CTOR_PARAM_RE = re.compile(r"InternalCallAuthenticationFilter internalCallAuthenticationFilter,\s*\n")
CTOR_PARAM_NEW = ("UserJwtAuthenticationFilter userJwtAuthenticationFilter,\n"
                  "            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,\n")
CTOR_PARAM_LAST_RE = re.compile(r"(\n\s*)InternalCallAuthenticationFilter internalCallAuthenticationFilter(\s*\)\s*\{)")

ASSIGN_OLD = "        this.internalCallAuthenticationFilter = internalCallAuthenticationFilter;"
ASSIGN_NEW = ("        this.userJwtAuthenticationFilter = userJwtAuthenticationFilter;\n"
              "        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;")

ADDBEFORE_OLD = ".addFilterBefore(internalCallAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)"
ADDBEFORE_NEW = (".addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)\n"
                 "            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)")

for m in MODULES:
    path = os.path.join(ROOT, f"mall-{m}", "src", "main", "java", "com", "cloudmart", m, "config", "SecurityConfig.java")
    src = io.open(path, encoding="utf-8").read()
    orig = src
    if IMPORTS_ANCHOR in src:
        src = src.replace(IMPORTS_ANCHOR, IMPORTS_NEW, 1)
    else:
        # fallback: insert after package import block start (after first import line)
        idx = src.index("\nimport ") + 1
        src = src[:idx] + IMPORTS_NEW.replace(IMPORTS_ANCHOR, "") + src[idx:]
    src = src.replace(FIELD_OLD, FIELD_NEW, 1)
    src = CTOR_PARAM_RE.sub(CTOR_PARAM_NEW, src, count=1)
    src = CTOR_PARAM_LAST_RE.sub(lambda mo: mo.group(1) + "UserJwtAuthenticationFilter userJwtAuthenticationFilter,"
                                 + mo.group(1) + "        ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter"
                                 + mo.group(2), src, count=1) if CTOR_PARAM_RE.search(orig) is None or True else src
    src = src.replace(ASSIGN_OLD, ASSIGN_NEW, 1)
    src = src.replace(ADDBEFORE_OLD, ADDBEFORE_NEW, 1)
    assert "InternalCallAuthenticationFilter" not in src, f"{m}: leftover reference"
    io.open(path, "w", encoding="utf-8", newline="\n").write(src)
    old_filter = os.path.join(ROOT, f"mall-{m}", "src", "main", "java", "com", "cloudmart", m, "config", "InternalCallAuthenticationFilter.java")
    if os.path.exists(old_filter):
        os.remove(old_filter)
    print(f"OK {m}")
print("done")

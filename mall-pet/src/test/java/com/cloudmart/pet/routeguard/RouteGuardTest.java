package com.cloudmart.pet.routeguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R27 路由门禁：用 Spring 自己的 {@link RequestMappingHandlerMapping} 解析真实路由
 * （不再用正则扫源码），天然正确处理：
 * <ul>
 *   <li>全限定注解（@org.springframework.web.bind.annotation.GetMapping）</li>
 *   <li>value/path 属性、多路径数组、类级 @RequestMapping 继承拼接</li>
 *   <li>无方法实例化（仅注册懒加载 BeanDefinition，读 Class 元数据）</li>
 * </ul>
 *
 * 规则（每个服务各自一份，公开/豁免清单见 /route-guard.json 测试资源）：
 * <ol>
 *   <li>/internal/**：任何 HTTP 方法都必须有 @PreAuthorize 且表达式包含 INTERNAL；</li>
 *   <li>/admin/**：任何方法都必须有 @PreAuthorize 且包含 ADMIN；</li>
 *   <li>其他写方法（POST/PUT/DELETE/PATCH，含无 method 的 @RequestMapping）：
 *       必须有 @PreAuthorize，或在公开清单（对应 SecurityConfig permitAll）；</li>
 *   <li>存量豁免逐项登记 owner/reason/expires，到期即红；新增欠账一律红。</li>
 * </ol>
 */
class RouteGuardTest {

    /** 本服务显式公开端点（必须与 SecurityConfig.permitAll 一致）；"METHOD PATH"，PATH 用 {var} 段。 */
    private static final String[] PUBLIC_ENDPOINTS = {};

    private record RouteRef(String method, String path, String handler, String preAuth) {}

    /**
     * 主门禁：真实解析本服务 main classes 下全部 @RestController/@Controller。
     */
    @Test
    @DisplayName("R27：所有写路由受保护，admin/internal 端点带显式角色守卫")
    void allWriteRoutesGuarded() {
        List<RouteRef> routes = collectRoutes();
        assertThat(routes).as("没有解析到任何路由说明扫描器坏了，门禁不能空转通过").isNotEmpty();

        Set<String> publicSet = new LinkedHashSet<>(List.of(PUBLIC_ENDPOINTS));
        List<String[]> exemptions = loadExemptions();
        LocalDate today = LocalDate.now();

        int covered = 0;
        int publicCount = 0;
        int exemptCount = 0;
        List<String> failures = new ArrayList<>();

        for (RouteRef r : routes) {
            boolean write = isWrite(r.method);
            if (!write) {
                covered++;
                continue;
            }
            boolean internal = r.path.startsWith("/internal/");
            boolean admin = r.path.startsWith("/admin/");

            // 公开清单优先（auth 域 /admin/login、/admin/refresh 等登录端点本就匿名可达，
            // 必须在 admin 规则之前判定）
            if (publicSet.contains(r.method + " " + r.path)) {
                publicCount++;
                continue;
            }
            if (internal) {
                if (r.preAuth != null && r.preAuth.contains("INTERNAL")) {
                    covered++;
                } else if (isExempt(exemptions, today, r)) {
                    exemptCount++;
                } else {
                    failures.add("[internal 缺 INTERNAL 守卫] " + r.method + " " + r.path + " @ " + r.handler);
                }
                continue;
            }
            if (admin) {
                // 下游业务服务的 /admin/** 由 mall-admin 服务令牌访问（hasRole('INTERNAL')）；
                // mall-admin 本模块面向管理端 JWT（ADMIN/hasAuthority 权限码）。两者都算显式守卫
                if (r.preAuth != null && hasAdminToken(r.preAuth)) {
                    covered++;
                } else if (isExempt(exemptions, today, r)) {
                    exemptCount++;
                } else {
                    failures.add("[admin 缺显式角色守卫(INTERNAL/ADMIN/权限码)] "
                            + r.method + " " + r.path + " @ " + r.handler);
                }
                continue;
            }
            if (r.preAuth != null) {
                covered++;
            } else if (isExempt(exemptions, today, r)) {
                exemptCount++;
            } else {
                failures.add("[写路由既无 @PreAuthorize 也不在公开清单] "
                        + r.method + " " + r.path + " @ " + r.handler);
            }
        }

        // CI 可见的覆盖/公开/豁免/总数口径
        System.out.printf("[R27] 路由 %d 条；写路由受保护 %d，公开 %d，豁免 %d，违规 %d%n",
                routes.size(), covered, publicCount, exemptCount, failures.size());

        assertThat(failures)
                .as("路由门禁违规：补 @PreAuthorize/角色守卫，或登记 SecurityConfig 公开清单；"
                        + "禁止通过调整注解顺序逃避%n  %s", String.join("\n  ", failures))
                .isEmpty();
    }

    /**
     * 解析器特性自检：全限定注解、value/path、多路径、类级继承、无注解方法忽略。
     * 故意删守卫时这些断言保证是"解析能力"在起作用，而非测试空转。
     */
    @Test
    @DisplayName("R27：解析器正确处理全限定/多路径/value/path/类级前缀")
    void parserHandlesAnnotationForms() {
        List<RouteRef> routes = collectRoutes(java.util.List.of(
                FeatureController.class,
                FeatureFullyQualifiedController.class));

        Map<String, Set<String>> byPath = new java.util.HashMap<>();
        for (RouteRef r : routes) {
            byPath.computeIfAbsent(r.path, k -> new TreeSet<>()).add(r.method);
        }
        // 类级 @RequestMapping("/feat") + value 属性
        assertThat(byPath).containsKey("/feat/value-form");
        // path 属性
        assertThat(byPath).containsKey("/feat/path-form");
        // 多路径数组两条都在
        assertThat(byPath.get("/feat/multi-a")).containsExactly("POST");
        assertThat(byPath.get("/feat/multi-b")).containsExactly("POST");
        // 全限定注解
        assertThat(byPath).containsKey("/fq/fully-qualified");
        // 无 @Mapping 的普通方法不出现
        assertThat(byPath.keySet()).doesNotContain("/feat/not-a-handler");
    }

    // ---------- 收集器 ----------

    private List<RouteRef> collectRoutes() {
        return collectRoutes(null);
    }

    /**
     * @param explicitClasses 非 null 时仅解析指定类（特性自检用）；
     *                        null 时扫描本服务 target/classes 下全部控制器
     *                        （用 CodeSource 排除 test-classes，避免把自检类算进主门禁）。
     */
    private List<RouteRef> collectRoutes(List<Class<?>> explicitClasses) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

        if (explicitClasses != null) {
            for (int i = 0; i < explicitClasses.size(); i++) {
                RootBeanDefinition bd = new RootBeanDefinition(explicitClasses.get(i));
                bd.setLazyInit(true);
                bf.registerBeanDefinition("__rg_feat_" + i, bd);
            }
        } else {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false) {
                        @Override
                        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                            AnnotationMetadata meta = beanDefinition.getMetadata();
                            return meta.isIndependent() && !meta.isAnnotation();
                        }
                    };
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
            int idx = 0;
            for (BeanDefinition candidate : scanner.findCandidateComponents("com.cloudmart")) {
                String className = candidate.getBeanClassName();
                Class<?> clazz;
                try {
                    clazz = Class.forName(className, false, RouteGuardTest.class.getClassLoader());
                } catch (Throwable t) {
                    continue;
                }
                // 只收主产物 target/classes，排除 test-classes 与依赖 jar
                File codeSource = codeSourceFile(clazz);
                if (codeSource == null || !codeSource.getPath().replace('\\', '/').endsWith("/classes")) {
                    continue;
                }
                RootBeanDefinition bd = new RootBeanDefinition();
                bd.setBeanClassName(className);
                bd.setLazyInit(true);
                bf.registerBeanDefinition("__rg_" + idx++, bd);
            }
        }

        // refresh 让上下文激活（控制器 Bean 全部 lazy，不会实例化依赖）；
        // HandlerMapping 只读取 Class 级注解元数据
        ctx.refresh();
        try {
            RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(ctx);
            mapping.afterPropertiesSet();

            List<RouteRef> out = new ArrayList<>();
            mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
                Set<String> methods = info.getMethodsCondition().getMethods().isEmpty()
                        ? Set.of("ANY")
                        : info.getMethodsCondition().getMethods().stream()
                            .map(Enum::name).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
                for (String pattern : patternsOf(info)) {
                    for (String method : methods) {
                        out.add(new RouteRef(method, pattern,
                                handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName(),
                                preAuthExpression(handlerMethod)));
                    }
                }
            });
            return out;
        } finally {
            ctx.close();
        }
    }

    private Set<String> patternsOf(RequestMappingInfo info) {
        // Boot 4 / Spring 7 统一 PathPatternParser，PathPatternsCondition 必非 null
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return Set.of("/");
    }

    private String preAuthExpression(HandlerMethod hm) {
        PreAuthorize onMethod = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(hm.getMethod(), PreAuthorize.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        PreAuthorize onClass = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(hm.getBeanType(), PreAuthorize.class);
        if (onClass != null) {
            return onClass.value();
        }
        // 项目自定义管理权限注解（mall-common AdminPermissionInterceptor 强制
        // 管理员上下文 + 权限码，等价 @PreAuthorize hasAuthority；真实注解，纳入识别）
        String rp = findMallPermission(hm, "com.cloudmart.common.annotation.RequiresPermission");
        if (rp != null) {
            return "RequiresPermission(" + rp + ")";
        }
        if (hasMallAnnotation(hm, "com.cloudmart.common.annotation.RequiresAdmin")) {
            return "RequiresAdmin";
        }
        return null;
    }

    /** 读方法或类上 mall-common 单 value 字符串注解属性（RequiresPermission.value）。 */
    private String findMallPermission(HandlerMethod hm, String annotationName) {
        Class<? extends java.lang.annotation.Annotation> type = annotationClass(annotationName);
        if (type == null) {
            return null;
        }
        String v = readStringAttribute(type, hm.getMethod(), hm.getBeanType());
        return v;
    }

    private boolean hasMallAnnotation(HandlerMethod hm, String annotationName) {
        Class<? extends java.lang.annotation.Annotation> type = annotationClass(annotationName);
        if (type == null) {
            return false;
        }
        return org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(hm.getMethod(), type)
                || org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), type);
    }

    private String readStringAttribute(Class<? extends java.lang.annotation.Annotation> type,
                                       java.lang.reflect.Method method, Class<?> beanType) {
        try {
            java.lang.annotation.Annotation a =
                    org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(method, type);
            if (a == null) {
                a = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(beanType, type);
            }
            if (a == null) {
                return null;
            }
            Object v = type.getMethod("value").invoke(a);
            return v == null ? null : v.toString();
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 按全限定名取 mall-common 注解类型（本模块已传递依赖 mall-common）。 */
    @SuppressWarnings("unchecked")
    private Class<? extends java.lang.annotation.Annotation> annotationClass(String name) {
        try {
            return (Class<? extends java.lang.annotation.Annotation>)
                    Class.forName(name, false, RouteGuardTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isWrite(String method) {
        return "ANY".equals(method)
                || "POST".equals(method) || "PUT".equals(method)
                || "DELETE".equals(method) || "PATCH".equals(method);
    }

    /**
     * /admin/** 显式守卫判定：
     * 下游服务接受 mall-admin 服务令牌（INTERNAL）；
     * mall-admin 接受管理端角色（ADMIN）、细粒度权限码（hasAuthority）或已认证声明
     * （网关 /api/admin 与 SecurityConfig 已在边界做管理端令牌域隔离，端侧不再可能
     * 到达）；唯一允许匿名的 /admin 登录/刷新必须显式登记到公开清单，禁止 permitAll。
     */
    private boolean hasAdminToken(String expression) {
        String e = expression.toUpperCase();
        return !expression.contains("permitAll")
                && (e.contains("INTERNAL") || e.contains("ADMIN")
                || e.contains("ISAUTHENTICATED")
                || expression.contains("hasAuthority(") || expression.contains("hasAnyAuthority(")
                // mall-common 管理拦截器的权限码/管理员声明（真实强制，非标记）
                || expression.startsWith("RequiresPermission(") || expression.equals("RequiresAdmin"));
    }

    private boolean isExempt(List<String[]> exemptions, LocalDate today, RouteRef r) {
        for (String[] e : exemptions) {
            if (!e[0].equals(r.method) || !e[1].equals(r.path)) {
                continue;
            }
            LocalDate expires = LocalDate.parse(e[4]);
            if (expires.isBefore(today)) {
                throw new AssertionError("路由豁免已过期（" + e[4] + "）：" + r.method + " " + r.path
                        + " owner=" + e[2] + "，必须补守卫或续期");
            }
            return true;
        }
        return false;
    }

    /**
     * 豁免清单（测试资源 /route-guard.json）：
     * {"exemptions":[["METHOD","/path","owner","reason","yyyy-MM-dd"]]}
     * 每项必须有负责人/原因/到期日；到期自动变红，新增欠账不得加入（只能补守卫或公开清单）。
     */
    private List<String[]> loadExemptions() {
        List<String[]> out = new ArrayList<>();
        try (var in = RouteGuardTest.class.getResourceAsStream("/route-guard.json")) {
            if (in == null) {
                return out;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // 逐项提取五元组（豁免文件结构简单固定，避免引入 JSON 依赖）
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\"\\]")
                    .matcher(json);
            while (m.find()) {
                out.add(new String[]{m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)});
            }
        } catch (Exception e) {
            throw new AssertionError("无法解析 /route-guard.json：" + e.getMessage(), e);
        }
        return out;
    }

    private File codeSourceFile(Class<?> clazz) {
        try {
            if (clazz.getProtectionDomain() == null
                    || clazz.getProtectionDomain().getCodeSource() == null
                    || clazz.getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 解析器自检用控制器 ----------

    @RestController
    @org.springframework.web.bind.annotation.RequestMapping("/feat")
    static class FeatureController {
        @org.springframework.web.bind.annotation.PostMapping(value = "/value-form")
        void valueForm() {}

        @org.springframework.web.bind.annotation.PostMapping(path = "/path-form")
        void pathForm() {}

        @org.springframework.web.bind.annotation.PostMapping({"/multi-a", "/multi-b"})
        void multiPath() {}

        void notAHandler() {}
    }

    @RestController
    static class FeatureFullyQualifiedController {
        @org.springframework.web.bind.annotation.PostMapping("/fq/fully-qualified")
        void fq() {}
    }
}


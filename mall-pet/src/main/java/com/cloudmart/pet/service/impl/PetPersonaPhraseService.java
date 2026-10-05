package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.entity.PetPersonaPhrase;
import com.cloudmart.pet.repository.PetPersonaPhraseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 人设口头禅服务（F8 配置化）：DB 权威 + 60 秒 TTL 缓存定时同步。
 *
 * <p>取值优先级：DB 行 → Nacos 出厂默认（{@code pet.chat.persona-phrases}）→ 空串。
 * 管理端保存后调 {@link #invalidate()} 本实例即时生效；其他实例靠 60 秒 TTL 轮询收敛
 * （"定时同步"）——多实例间配置延迟 ≤60 秒，DB 抖动沿用旧快照（Fail-Open）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetPersonaPhraseService {

    /** 列表接口展示"出厂默认"的占位标记（真实取值见 pet.chat.persona-phrases 配置） */
    public static final String PHRASE_PLACEHOLDER = "（出厂默认，保存后生效）";

    private static final long CACHE_TTL_NANOS = 60L * 1_000_000_000L;

    private final PetPersonaPhraseMapper phraseMapper;
    private final com.cloudmart.pet.config.PetProperties properties;

    private final AtomicReference<CachedPhrases> cache =
            new AtomicReference<>(new CachedPhrases(Map.of(), 0));

    private record CachedPhrases(Map<String, String> phrases, long expiresAtNanos) {
    }

    /**
     * 保存（管理端 upsert：按性格 uk，存在即更新）；
     *
     * @return 行主键（PET-20：审计快照定位真实行，不再以 0 占位——原实现用未注册类型
     *         +0 主键调快照，写入成功而接口必然失败）
     */
    public Long save(String personality, String phrase) {
        Long rowId;
        PetPersonaPhrase existing = phraseMapper.selectOne(new LambdaQueryWrapper<PetPersonaPhrase>()
                .eq(PetPersonaPhrase::getPersonality, personality)
                .last("LIMIT 1"));
        if (existing != null) {
            phraseMapper.update(null, new LambdaUpdateWrapper<PetPersonaPhrase>()
                    .set(PetPersonaPhrase::getPhrase, phrase)
                    .eq(PetPersonaPhrase::getId, existing.getId()));
            rowId = existing.getId();
        } else {
            PetPersonaPhrase row = new PetPersonaPhrase();
            row.setPersonality(personality);
            row.setPhrase(phrase);
            try {
                phraseMapper.insert(row);
                rowId = row.getId();
            } catch (org.springframework.dao.DuplicateKeyException e) {
                PetPersonaPhrase concurrent = phraseMapper.selectOne(new LambdaQueryWrapper<PetPersonaPhrase>()
                        .eq(PetPersonaPhrase::getPersonality, personality)
                        .last("LIMIT 1"));
                if (concurrent == null) {
                    throw new IllegalStateException("口头禅并发保存失败: " + personality);
                }
                phraseMapper.update(null, new LambdaUpdateWrapper<PetPersonaPhrase>()
                        .set(PetPersonaPhrase::getPhrase, phrase)
                        .eq(PetPersonaPhrase::getId, concurrent.getId()));
                rowId = concurrent.getId();
            }
        }
        invalidate();
        return rowId;
    }

    /** 管理端保存后调用：本实例即时生效（其他实例靠 TTL 收敛） */
    public void invalidate() {
        cache.set(new CachedPhrases(Map.of(), 0));
    }

    /**
     * 性格 → 口头禅（{name} 占位宠物名）：DB 权威，Nacos 兜底，未配置返回空串。
     * petName 由调用方替换（缓存的是模板，不缓存替换结果）。
     */
    public String phraseOf(String personality, String petName) {
        String template = phrases().get(personality != null ? personality : "LIVELY");
        if (template == null || template.isBlank()) {
            template = properties.getChat().getPersonaPhrases()
                    .getOrDefault(personality != null ? personality : "LIVELY", "");
        }
        return template.replace("{name}", petName);
    }

    private Map<String, String> phrases() {
        CachedPhrases cached = cache.get();
        if (cached.expiresAtNanos() > System.nanoTime() && !cached.phrases().isEmpty()) {
            return cached.phrases();
        }
        try {
            Map<String, String> phrases = new java.util.HashMap<>();
            phraseMapper.selectList(null)
                    .forEach(row -> phrases.put(row.getPersonality(), row.getPhrase()));
            cache.set(new CachedPhrases(Map.copyOf(phrases), System.nanoTime() + CACHE_TTL_NANOS));
            return phrases;
        } catch (Exception e) {
            log.warn("口头禅缓存刷新失败（沿用旧快照/兜底配置）", e);
            return cached.phrases();
        }
    }
}

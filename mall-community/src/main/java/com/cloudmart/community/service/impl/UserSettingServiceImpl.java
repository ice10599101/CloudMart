package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.UserSetting;
import com.cloudmart.community.repository.UserSettingMapper;
import com.cloudmart.community.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSettingServiceImpl implements UserSettingService {

    private final UserSettingMapper userSettingMapper;

    @Override
    public Map<String, String> getUserSettings(Long userId) {
        List<UserSetting> settings = userSettingMapper.selectList(
                new LambdaQueryWrapper<UserSetting>().eq(UserSetting::getUserId, userId));
        Map<String, String> result = new LinkedHashMap<>();
        for (UserSetting setting : settings) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }

    @Override
    @Transactional
    public void updateUserSettings(Long userId, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) {
                continue;
            }

            UserSetting existing = userSettingMapper.selectOne(
                    new LambdaQueryWrapper<UserSetting>()
                            .eq(UserSetting::getUserId, userId)
                            .eq(UserSetting::getSettingKey, key));
            if (existing != null) {
                existing.setSettingValue(value != null ? value : "");
                userSettingMapper.updateById(existing);
            } else {
                UserSetting newSetting = new UserSetting();
                newSetting.setUserId(userId);
                newSetting.setSettingKey(key);
                newSetting.setSettingValue(value != null ? value : "");
                userSettingMapper.insert(newSetting);
            }
        }
    }

    @Override
    public String getSetting(Long userId, String key, String defaultValue) {
        UserSetting setting = userSettingMapper.selectOne(
                new LambdaQueryWrapper<UserSetting>()
                        .eq(UserSetting::getUserId, userId)
                        .eq(UserSetting::getSettingKey, key));
        return setting != null ? setting.getSettingValue() : defaultValue;
    }

    @Override
    public boolean getBooleanSetting(Long userId, String key, boolean defaultValue) {
        String value = getSetting(userId, key, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}

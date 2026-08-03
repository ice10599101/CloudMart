package com.cloudmart.community.service;

import java.util.Map;

public interface UserSettingService {

    Map<String, String> getUserSettings(Long userId);

    void updateUserSettings(Long userId, Map<String, String> settings);

    String getSetting(Long userId, String key, String defaultValue);

    boolean getBooleanSetting(Long userId, String key, boolean defaultValue);
}

import { useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Input, ScrollView, Text, View } from '@tarojs/components'
import { petApi, type PetInfo } from '@/api/pet'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { PET_CREAM_STYLE, PET_CREAM_EXT_STYLE } from '@/styles/petCream'
import {
  ACCESSORIES,
  GENDER_OPTIONS,
  PERSONALITY_OPTIONS,
  PET_COLORS,
  SPECIES_OPTIONS,
  friendlyError,
} from './constants'
import styles from './index.module.scss'

/**
 * 领养向导（无宠物时整屏展示）：物种 / 性别 / 性格 / 外观颜色 / 配饰 / 名字。
 * R30 自 index.tsx 拆出，行为不变；领养成功后回调主页刷新（后端 404→有宠状态切换）。
 */
export function AdoptView({ onAdopted }: { onAdopted: () => void }) {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [adoptSpecies, setAdoptSpecies] = useState('STRAWBERRY')
  const [adoptGender, setAdoptGender] = useState('MALE')
  const [adoptPersonality, setAdoptPersonality] = useState('LIVELY')
  const [adoptName, setAdoptName] = useState('')
  // 领养外观（原文档 §6 外观系统）
  const [adoptColor, setAdoptColor] = useState('orange')
  const [adoptAccessory, setAdoptAccessory] = useState('none')

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  const adopt = async () => {
    if (!adoptName.trim()) {
      toast('先给宠物取个名字吧')
      return
    }
    try {
      await petApi.createPet({
        name: adoptName.trim(),
        species: adoptSpecies as PetInfo['species'],
        gender: adoptGender as PetInfo['gender'],
        personality: adoptPersonality as PetInfo['personality'],
        color: adoptColor,
        accessory: adoptAccessory,
      })
      Taro.showToast({ title: '领养成功！', icon: 'success' })
      setAdoptName('')
      onAdopted()
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  return (
    <View className={`${styles.page} ${dataTheme}`} style={{ ...themeStyle, ...PET_CREAM_STYLE, ...PET_CREAM_EXT_STYLE }}>
      <CustomNavBar title="我的宠物" />
      <ScrollView scrollY className={styles.adoptScroll} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        <Text className={styles.adoptTitle}>🐾 领养一只属于你的宠物</Text>
        <Text className={styles.adoptDesc}>陪你打工、读书、捞漂流瓶、聊天</Text>
        <View className={styles.speciesRow}>
          {SPECIES_OPTIONS.map((option) => (
            <View
              key={option.value}
              className={`${styles.speciesCard} ${adoptSpecies === option.value ? styles.speciesActive : ''}`}
              onClick={() => setAdoptSpecies(option.value)}
            >
              <Text className={styles.speciesEmoji}>{option.emoji}</Text>
              <Text className={styles.speciesLabel}>{option.label}</Text>
            </View>
          ))}
        </View>
        <Text className={styles.sectionTitle}>性别</Text>
        <View className={styles.personalityRow}>
          {GENDER_OPTIONS.map((option) => (
            <View
              key={option.value}
              className={`${styles.personalityChip} ${styles.genderChip} ${adoptGender === option.value ? styles.personalityActive : ''}`}
              onClick={() => setAdoptGender(option.value)}
            >
              <Text>{option.label}</Text>
            </View>
          ))}
        </View>
        <View className={styles.personalityRow}>
          {PERSONALITY_OPTIONS.map((option) => (
            <View
              key={option.value}
              className={`${styles.personalityChip} ${adoptPersonality === option.value ? styles.personalityActive : ''}`}
              onClick={() => setAdoptPersonality(option.value)}
            >
              <Text>{option.label}</Text>
            </View>
          ))}
        </View>
        <Text className={styles.sectionTitle}>外观颜色</Text>
        <View className={styles.personalityRow}>
          {PET_COLORS.map((color) => (
            <View
              key={color}
              className={`${styles.personalityChip} ${adoptColor === color ? styles.personalityActive : ''}`}
              onClick={() => setAdoptColor(color)}
            >
              <Text>{color}</Text>
            </View>
          ))}
        </View>
        <Text className={styles.sectionTitle}>配饰</Text>
        <View className={styles.personalityRow}>
          {ACCESSORIES.map((item) => (
            <View
              key={item.key}
              className={`${styles.personalityChip} ${adoptAccessory === item.key ? styles.personalityActive : ''}`}
              onClick={() => setAdoptAccessory(item.key)}
            >
              <Text>{item.label}</Text>
            </View>
          ))}
        </View>
        <Text className={styles.sectionTitle}>名字</Text>
        <Input
          className={styles.nameInput}
          value={adoptName}
          maxlength={12}
          placeholder="给它取个名字（1-12 字）"
          onInput={(e) => setAdoptName(e.detail.value)}
        />
        <Button className={styles.primaryBtn} onClick={() => void adopt()}>领养它</Button>
      </ScrollView>
    </View>
  )
}

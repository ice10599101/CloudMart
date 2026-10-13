// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ./shared
import { useState } from 'react'
import { ScrollView, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo } from '@/api/pet'
import { ACCESSORIES, ChipButton, PERSONALITY_LABEL, PET_COLORS, SPECIES_EMOJI } from './shared'

/** 整屏领养向导：领养成功后回调宿主刷新（宿主据此离开领养态） */
export function AdoptView({ onAdopted }: { onAdopted: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [species, setSpecies] = useState('STRAWBERRY')
  const [gender, setGender] = useState('MALE')
  const [personality, setPersonality] = useState('LIVELY')
  const [color, setColor] = useState('orange')
  const [accessory, setAccessory] = useState('none')
  const [name, setName] = useState('')

  const adopt = async () => {
    if (!name.trim()) return
    try {
      await petApi.createPet({
        name: name.trim(),
        species: species as PetInfo['species'],
        gender: gender as PetInfo['gender'],
        personality: personality as PetInfo['personality'],
        color,
        accessory,
      })
      setName('')
      onAdopted()
    } catch {
      // 拦截器已提示
    }
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.lg }}>
      <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: colors.text, textAlign: 'center', marginTop: Spacing.xl }}>
        🐾 领养一只属于你的宠物
      </Text>
      <Text style={{ fontSize: FontSize.sm, color: colors.textSecondary, textAlign: 'center', marginTop: Spacing.xs, marginBottom: Spacing.lg }}>
        陪你打工、读书、捞漂流瓶、聊天
      </Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, justifyContent: 'center' }}>
        {Object.entries(SPECIES_EMOJI).map(([key, emoji]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setSpecies(key)}
            style={{
              width: 88, alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg,
              borderWidth: 2, borderColor: species === key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ fontSize: 36 }}>{emoji}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>性别</Text>
      <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'center' }}>
        {([['MALE', '♂ 男'], ['FEMALE', '♀ 女']] as const).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setGender(key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 18, borderRadius: 999,
              borderWidth: 2, borderColor: gender === key ? colors.primary : colors.border,
              backgroundColor: gender === key ? colors.primary : 'transparent',
            }}
          >
            <Text style={{ color: gender === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs, fontWeight: '600' }}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center', marginTop: Spacing.md }}>
        {Object.entries(PERSONALITY_LABEL).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setPersonality(key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
              borderWidth: 1, borderColor: personality === key ? colors.primary : colors.border,
              backgroundColor: personality === key ? colors.primary : 'transparent',
            }}
          >
            <Text style={{ color: personality === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>外观颜色</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center' }}>
        {PET_COLORS.map((key) => (
          <TouchableOpacity
            key={key}
            onPress={() => setColor(key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
              borderWidth: 1, borderColor: color === key ? colors.primary : colors.border,
              backgroundColor: color === key ? colors.primary : 'transparent',
            }}
          >
            <Text style={{ color: color === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{key}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>配饰</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center' }}>
        {ACCESSORIES.map((item) => (
          <TouchableOpacity
            key={item.key}
            onPress={() => setAccessory(item.key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
              borderWidth: 1, borderColor: accessory === item.key ? colors.primary : colors.border,
              backgroundColor: accessory === item.key ? colors.primary : 'transparent',
            }}
          >
            <Text style={{ color: accessory === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <TextInput
        value={name}
        onChangeText={setName}
        maxLength={12}
        placeholder="给它取个名字（1-12 字）"
        placeholderTextColor={colors.textTertiary}
        style={{
          marginTop: Spacing.lg, paddingHorizontal: Spacing.md, paddingVertical: 12,
          borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border,
          color: colors.text, backgroundColor: colors.bgContainer,
        }}
      />
      <View style={{ marginTop: Spacing.md }}>
        <ChipButton label="领养它" primary onPress={adopt} disabled={!name.trim()} />
      </View>
    </ScrollView>
  )
}

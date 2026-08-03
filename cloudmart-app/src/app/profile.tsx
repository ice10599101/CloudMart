import { View, Text, TextInput, ScrollView, TouchableOpacity, Image, Alert } from 'react-native'
import { useState, useEffect } from 'react'
import { router } from 'expo-router'
import * as ImagePicker from 'expo-image-picker'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { userApi } from '@/api/user'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import axios from 'axios'
import { storage } from '@/utils/storage'

const GENDER_OPTIONS = ['未设置', '男', '女']
const GENDER_VALUES = ['UNKNOWN', 'MALE', 'FEMALE']
const CONSTELLATIONS = [
  '白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座',
  '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座',
]

function FormField({ label, children, theme }: { label: string; children: React.ReactNode; theme: ReturnType<typeof useTheme> }) {
  return (
    <View style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
      <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>{label}</Text>
      {children}
    </View>
  )
}

function FormInput({ value, onChange, placeholder, theme, multiline = false, maxLength }: {
  value: string; onChange: (v: string) => void; placeholder: string; theme: ReturnType<typeof useTheme>
  multiline?: boolean; maxLength?: number
}) {
  return (
    <TextInput
      value={value}
      onChangeText={onChange}
      placeholder={placeholder}
      placeholderTextColor={theme.textTertiary}
      multiline={multiline}
      maxLength={maxLength}
      style={{
        fontSize: FontSize.md, color: theme.text,
        borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md,
        paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm,
        backgroundColor: theme.bgBase,
        minHeight: multiline ? 80 : 40,
        textAlignVertical: multiline ? 'top' : 'center',
      }}
    />
  )
}

function PickerField({ value, options, onSelect, theme, placeholder }: {
  value: string; options: string[]; onSelect: (v: string) => void; theme: ReturnType<typeof useTheme>
  placeholder: string
}) {
  return (
    <TouchableOpacity
      onPress={() => Alert.alert(
        placeholder,
        undefined,
        options.map((opt) => ({
          text: opt, onPress: () => onSelect(opt),
        })).concat({ text: '取消', onPress: () => {} }),
        { cancelable: true },
      )}
      style={{
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md,
        paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm,
        backgroundColor: theme.bgBase, minHeight: 40,
      }}
    >
      <Text style={{ fontSize: FontSize.md, color: value ? theme.text : theme.textTertiary }}>
        {value || placeholder}
      </Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
    </TouchableOpacity>
  )
}

export default function ProfilePage() {
  const theme = useTheme()
  const { user, fetchUser } = useAuthStore()

  const [nickname, setNickname] = useState('')
  const [signature, setSignature] = useState('')
  const [gender, setGender] = useState('UNKNOWN')
  const [birthday, setBirthday] = useState('')
  const [constellation, setConstellation] = useState('')
  const [occupation, setOccupation] = useState('')
  const [school, setSchool] = useState('')
  const [location, setLocation] = useState('')
  const [hobbies, setHobbies] = useState('')
  const [saving, setSaving] = useState(false)
  const [avatarUploading, setAvatarUploading] = useState(false)

  useEffect(() => {
    if (user) {
      setNickname(user.nickname || '')
      setSignature(user.signature || '')
      setGender(user.gender || 'UNKNOWN')
      setBirthday(user.birthday || '')
      setConstellation(user.constellation || '')
      setOccupation(user.occupation || '')
      setSchool(user.school || '')
      setLocation(user.location || '')
      setHobbies(user.hobbies || '')
    }
  }, [user])

  const handleAvatarUpload = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        quality: 0.8,
        allowsEditing: true,
        aspect: [1, 1],
      })
      if (result.canceled || !result.assets?.[0]) return

      setAvatarUploading(true)
      const token = await storage.getItem('access_token')
      const formData = new FormData()
      formData.append('file', {
        uri: result.assets[0].uri,
        type: 'image/jpeg',
        name: 'avatar.jpg',
      } as unknown as Blob)

      const uploadRes = await axios.post(`${window.location.protocol}//${window.location.hostname}:8090/api/file/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data', Authorization: token ? `Bearer ${token}` : '' },
      })
      const avatarUrl = uploadRes.data?.data?.url
      if (avatarUrl) {
        await userApi.updateProfile({ avatar: avatarUrl })
        await fetchUser()
        Alert.alert('成功', '头像更新成功')
      }
    } catch {
      Alert.alert('错误', '头像上传失败')
    } finally {
      setAvatarUploading(false)
    }
  }

  const handleSave = async () => {
    if (!nickname.trim()) {
      Alert.alert('提示', '请输入昵称')
      return
    }
    setSaving(true)
    try {
      await userApi.updateProfile({ nickname, signature, gender, birthday, constellation, occupation, school, location, hobbies })
      await fetchUser()
      router.back()
    } catch {
      Alert.alert('错误', '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const genderLabel = GENDER_OPTIONS[GENDER_VALUES.indexOf(gender)] || '未设置'

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ paddingBottom: Spacing.xxxl }}>
        {/* Avatar Section */}
        <View style={{ alignItems: 'center', paddingVertical: Spacing.xxl }}>
          <TouchableOpacity onPress={handleAvatarUpload} disabled={avatarUploading}>
            {user?.avatar ? (
              <Image source={{ uri: user.avatar }} style={{ width: 90, height: 90, borderRadius: 45, borderWidth: 3, borderColor: theme.primary }} />
            ) : (
              <View style={{
                width: 90, height: 90, borderRadius: 45,
                backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center',
                borderWidth: 3, borderColor: theme.primary,
              }}>
                <Text style={{ fontSize: 36, color: theme.primary, fontWeight: 'bold' }}>{user?.nickname?.[0] || 'U'}</Text>
              </View>
            )}
            <View style={{
              position: 'absolute', right: 0, bottom: 0,
              width: 28, height: 28, borderRadius: 14,
              backgroundColor: theme.bgContainer, borderWidth: 1, borderColor: theme.border,
              justifyContent: 'center', alignItems: 'center',
            }}>
              <Text style={{ fontSize: 14 }}>📷</Text>
            </View>
          </TouchableOpacity>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.sm }}>
            {avatarUploading ? '上传中...' : '点击更换头像'}
          </Text>
        </View>

        {/* Form Section */}
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, paddingHorizontal: Spacing.lg }}>
          <FormField label="昵称" theme={theme}>
            <FormInput value={nickname} onChange={setNickname} placeholder="请输入昵称" theme={theme} />
          </FormField>

          <FormField label="个性签名" theme={theme}>
            <FormInput value={signature} onChange={setSignature} placeholder="写一句话介绍自己" theme={theme} multiline maxLength={100} />
          </FormField>

          <FormField label="性别" theme={theme}>
            <PickerField value={genderLabel} options={GENDER_OPTIONS} onSelect={(opt) => setGender(GENDER_VALUES[GENDER_OPTIONS.indexOf(opt)])} theme={theme} placeholder="请选择性别" />
          </FormField>

          <FormField label="生日" theme={theme}>
            <FormInput value={birthday} onChange={setBirthday} placeholder="例如：2000-01-01" theme={theme} />
          </FormField>

          <FormField label="星座" theme={theme}>
            <PickerField value={constellation} options={CONSTELLATIONS} onSelect={setConstellation} theme={theme} placeholder="请选择星座" />
          </FormField>

          <FormField label="职业" theme={theme}>
            <FormInput value={occupation} onChange={setOccupation} placeholder="例如：设计师、程序员、学生" theme={theme} />
          </FormField>

          <FormField label="学校" theme={theme}>
            <FormInput value={school} onChange={setSchool} placeholder="例如：北京大学" theme={theme} />
          </FormField>

          <FormField label="所在地区" theme={theme}>
            <FormInput value={location} onChange={setLocation} placeholder="例如：北京·朝阳区" theme={theme} />
          </FormField>

          <FormField label="兴趣爱好" theme={theme}>
            <FormInput value={hobbies} onChange={setHobbies} placeholder="多个爱好用逗号分隔" theme={theme} multiline maxLength={200} />
          </FormField>
        </View>

        {/* Save Button */}
        <TouchableOpacity
          onPress={saving ? undefined : handleSave}
          style={{
            marginHorizontal: Spacing.lg, marginTop: Spacing.xl,
            height: 48, borderRadius: BorderRadius.xl,
            backgroundColor: theme.primary, justifyContent: 'center', alignItems: 'center',
            opacity: saving ? 0.6 : 1,
          }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>
            {saving ? '保存中...' : '保存'}
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
  )
}

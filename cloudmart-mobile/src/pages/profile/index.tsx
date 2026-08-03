import { useState, useEffect } from 'react'
import { View, Text, Input, Textarea, Picker, Image } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import { userApi } from '@/api/user'
import { fileApi } from '@/api/file'
import styles from './index.module.scss'

const GENDER_OPTIONS = ['未设置', '男', '女']
const GENDER_VALUES = ['UNKNOWN', 'MALE', 'FEMALE']
const CONSTELLATIONS = [
  '白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座',
  '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座',
]

export default function ProfilePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { user, fetchUser } = useAuthStore()
  useAuthGuard()

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

  useDidShow(() => {
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
  })

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
      const res = await Taro.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
      })
      const tempFilePath = res.tempFilePaths[0]

      const fileInfo = await Taro.getFileInfo({ filePath: tempFilePath }) as { size: number }
      if (fileInfo.size > 5 * 1024 * 1024) {
        Taro.showToast({ title: '头像不能超过5MB', icon: 'none' })
        return
      }

      setAvatarUploading(true)
      const uploadRes = await fileApi.upload(tempFilePath)
      const avatarUrl = uploadRes.data.data.url
      await userApi.updateProfile({ avatar: avatarUrl })
      await fetchUser()
      Taro.showToast({ title: '头像更新成功', icon: 'success' })
    } catch {
      Taro.showToast({ title: '头像上传失败', icon: 'none' })
    } finally {
      setAvatarUploading(false)
    }
  }

  const handleSave = async () => {
    if (!nickname.trim()) {
      Taro.showToast({ title: '请输入昵称', icon: 'none' })
      return
    }
    setSaving(true)
    try {
      await userApi.updateProfile({
        nickname,
        signature,
        gender,
        birthday,
        constellation,
        occupation,
        school,
        location,
        hobbies,
      })
      await fetchUser()
      Taro.showToast({ title: '保存成功', icon: 'success' })
      setTimeout(() => Taro.navigateBack(), 500)
    } catch {
      Taro.showToast({ title: '保存失败', icon: 'none' })
    } finally {
      setSaving(false)
    }
  }

  const genderIndex = GENDER_VALUES.indexOf(gender)
  const constellationIndex = constellation ? CONSTELLATIONS.indexOf(constellation) : -1

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {/* Avatar Section */}
      <View className={styles.avatarSection}>
        <View className={styles.avatarWrap} onClick={handleAvatarUpload}>
          {user?.avatar ? (
            <Image className={styles.avatar} src={user.avatar} mode='aspectFill' />
          ) : (
            <View className={styles.avatarPlaceholder}>
              <Text className={styles.avatarText}>{user?.nickname?.charAt(0) || 'U'}</Text>
            </View>
          )}
          {avatarUploading && <View className={styles.avatarOverlay}><Text className={styles.avatarOverlayText}>上传中</Text></View>}
          <View className={styles.avatarBadge}>
            <Text className={styles.avatarBadgeIcon}>📷</Text>
          </View>
        </View>
        <Text className={styles.avatarHint}>{avatarUploading ? '上传中...' : '点击更换头像'}</Text>
      </View>

      {/* Form Section */}
      <View className={styles.section}>
        <View className={styles.field}>
          <Text className={styles.label}>昵称</Text>
          <Input
            className={styles.input}
            value={nickname}
            onInput={(e) => setNickname(e.detail.value)}
            placeholder='请输入昵称'
            placeholderClass={styles.placeholder}
          />
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>个性签名</Text>
          <Textarea
            className={styles.textarea}
            value={signature}
            onInput={(e) => setSignature(e.detail.value)}
            placeholder='写一句话介绍自己'
            placeholderClass={styles.placeholder}
            maxlength={100}
          />
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>性别</Text>
          <Picker mode='selector' range={GENDER_OPTIONS} value={genderIndex >= 0 ? genderIndex : 0} onChange={(e) => setGender(GENDER_VALUES[Number(e.detail.value)])}>
            <View className={styles.pickerValue}>
              <Text className={styles.pickerText}>{GENDER_OPTIONS[genderIndex >= 0 ? genderIndex : 0]}</Text>
              <Text className={styles.pickerArrow}>›</Text>
            </View>
          </Picker>
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>生日</Text>
          <Picker mode='date' value={birthday || '2000-01-01'} start='1950-01-01' end={new Date().toISOString().split('T')[0]} onChange={(e) => setBirthday(e.detail.value)}>
            <View className={styles.pickerValue}>
              <Text className={styles.pickerText}>{birthday || '请选择生日'}</Text>
              <Text className={styles.pickerArrow}>›</Text>
            </View>
          </Picker>
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>星座</Text>
          <Picker mode='selector' range={CONSTELLATIONS} value={constellationIndex >= 0 ? constellationIndex : 0} onChange={(e) => setConstellation(CONSTELLATIONS[Number(e.detail.value)])}>
            <View className={styles.pickerValue}>
              <Text className={styles.pickerText}>{constellation || '请选择星座'}</Text>
              <Text className={styles.pickerArrow}>›</Text>
            </View>
          </Picker>
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>职业</Text>
          <Input
            className={styles.input}
            value={occupation}
            onInput={(e) => setOccupation(e.detail.value)}
            placeholder='例如：设计师、程序员、学生'
            placeholderClass={styles.placeholder}
          />
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>学校</Text>
          <Input
            className={styles.input}
            value={school}
            onInput={(e) => setSchool(e.detail.value)}
            placeholder='例如：北京大学'
            placeholderClass={styles.placeholder}
          />
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>所在地区</Text>
          <Input
            className={styles.input}
            value={location}
            onInput={(e) => setLocation(e.detail.value)}
            placeholder='例如：北京·朝阳区'
            placeholderClass={styles.placeholder}
          />
        </View>

        <View className={styles.field}>
          <Text className={styles.label}>兴趣爱好</Text>
          <Textarea
            className={styles.textarea}
            value={hobbies}
            onInput={(e) => setHobbies(e.detail.value)}
            placeholder='多个爱好用逗号分隔'
            placeholderClass={styles.placeholder}
            maxlength={200}
          />
        </View>
      </View>

      {/* Save Button */}
      <View className={`${styles.saveBtn} ${saving ? styles.saveBtnDisabled : ''}`} onClick={saving ? undefined : handleSave}>
        <Text className={styles.saveText}>{saving ? '保存中...' : '保存'}</Text>
      </View>
    </View>
  )
}

import { useState } from 'react'
import { View, Text, Input, Switch } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import { userApi } from '@/api/user'
import type { Address } from '@/types'
import styles from './index.module.scss'

const EMPTY_FORM = { name: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false }

export default function AddressPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const [addresses, setAddresses] = useState<Address[]>([])
  const [loading, setLoading] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...EMPTY_FORM })

  useDidShow(() => {
    loadAddresses()
  })

  const loadAddresses = async () => {
    setLoading(true)
    try {
      const res = await userApi.getAddresses()
      setAddresses(res.data?.data || [])
    } catch {
      setAddresses([])
    } finally {
      setLoading(false)
    }
  }

  const handleAdd = () => {
    setEditingId(null)
    setForm({ ...EMPTY_FORM })
    setShowForm(true)
  }

  const handleEdit = (addr: Address) => {
    setEditingId(addr.id)
    setForm({
      name: addr.name,
      phone: addr.phone,
      province: addr.province,
      city: addr.city,
      district: addr.district,
      detail: addr.detail,
      isDefault: addr.isDefault,
    })
    setShowForm(true)
  }

  const handleDelete = async (id: number) => {
    const res = await Taro.showModal({ title: '提示', content: '确定删除该地址吗？' })
    if (!res.confirm) return
    try {
      await userApi.deleteAddress(id)
      Taro.showToast({ title: '删除成功', icon: 'success' })
      loadAddresses()
    } catch {
      Taro.showToast({ title: '删除失败', icon: 'none' })
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await userApi.setDefaultAddress(id)
      Taro.showToast({ title: '已设为默认', icon: 'success' })
      loadAddresses()
    } catch {
      Taro.showToast({ title: '设置失败', icon: 'none' })
    }
  }

  const handleSave = async () => {
    if (!form.name.trim()) {
      Taro.showToast({ title: '请输入收货人', icon: 'none' })
      return
    }
    if (!form.phone.trim() || !/^1\d{10}$/.test(form.phone)) {
      Taro.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (!form.province.trim() || !form.city.trim()) {
      Taro.showToast({ title: '请输入省市区', icon: 'none' })
      return
    }
    if (!form.detail.trim()) {
      Taro.showToast({ title: '请输入详细地址', icon: 'none' })
      return
    }

    try {
      if (editingId) {
        await userApi.updateAddress(editingId, form)
      } else {
        await userApi.createAddress(form)
      }
      Taro.showToast({ title: '保存成功', icon: 'success' })
      setShowForm(false)
      loadAddresses()
    } catch {
      Taro.showToast({ title: '保存失败', icon: 'none' })
    }
  }

  const updateForm = (key: string, value: string | boolean) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {!showForm ? (
        <>
          {/* Address List */}
          {addresses.length === 0 && !loading && (
            <View className={styles.empty}>
              <Text className={styles.emptyIcon}>📍</Text>
              <Text className={styles.emptyText}>暂无收货地址</Text>
            </View>
          )}

          {addresses.map((addr) => (
            <View key={addr.id} className={styles.addressCard}>
              <View className={styles.addressInfo}>
                <View className={styles.addressHeader}>
                  <Text className={styles.addressName}>{addr.name}</Text>
                  <Text className={styles.addressPhone}>{addr.phone}</Text>
                  {addr.isDefault && <View className={styles.defaultTag}><Text className={styles.defaultTagText}>默认</Text></View>}
                </View>
                <Text className={styles.addressDetail}>{addr.province}{addr.city}{addr.district} {addr.detail}</Text>
              </View>
              <View className={styles.addressActions}>
                {!addr.isDefault && (
                  <View className={styles.actionBtn} onClick={() => handleSetDefault(addr.id)}>
                    <Text className={styles.actionText}>设为默认</Text>
                  </View>
                )}
                <View className={styles.actionBtn} onClick={() => handleEdit(addr)}>
                  <Text className={styles.actionText}>编辑</Text>
                </View>
                <View className={`${styles.actionBtn} ${styles.actionDanger}`} onClick={() => handleDelete(addr.id)}>
                  <Text className={styles.actionDangerText}>删除</Text>
                </View>
              </View>
            </View>
          ))}

          {/* Add Button */}
          <View className={styles.addBtn} onClick={handleAdd}>
            <Text className={styles.addBtnText}>+ 新增收货地址</Text>
          </View>
        </>
      ) : (
        <>
          {/* Address Form */}
          <View className={styles.formSection}>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>收货人</Text>
              <Input className={styles.formInput} value={form.name} onInput={(e) => updateForm('name', e.detail.value)} placeholder='请输入收货人姓名' placeholderClass={styles.placeholder} />
            </View>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>手机号</Text>
              <Input className={styles.formInput} type='number' value={form.phone} onInput={(e) => updateForm('phone', e.detail.value)} placeholder='请输入手机号' placeholderClass={styles.placeholder} maxlength={11} />
            </View>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>省份</Text>
              <Input className={styles.formInput} value={form.province} onInput={(e) => updateForm('province', e.detail.value)} placeholder='例如：广东省' placeholderClass={styles.placeholder} />
            </View>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>城市</Text>
              <Input className={styles.formInput} value={form.city} onInput={(e) => updateForm('city', e.detail.value)} placeholder='例如：深圳市' placeholderClass={styles.placeholder} />
            </View>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>区/县</Text>
              <Input className={styles.formInput} value={form.district} onInput={(e) => updateForm('district', e.detail.value)} placeholder='例如：南山区' placeholderClass={styles.placeholder} />
            </View>
            <View className={styles.formField}>
              <Text className={styles.formLabel}>详细地址</Text>
              <Input className={styles.formInput} value={form.detail} onInput={(e) => updateForm('detail', e.detail.value)} placeholder='街道、楼牌号等' placeholderClass={styles.placeholder} />
            </View>
            <View className={styles.formSwitch}>
              <Text className={styles.formLabel}>设为默认地址</Text>
              <Switch checked={form.isDefault} onChange={(e) => updateForm('isDefault', e.detail.value)} color='var(--color-primary)' />
            </View>
          </View>

          <View className={styles.formActions}>
            <View className={styles.cancelBtn} onClick={() => setShowForm(false)}>
              <Text className={styles.cancelBtnText}>取消</Text>
            </View>
            <View className={styles.saveBtn} onClick={handleSave}>
              <Text className={styles.saveBtnText}>保存</Text>
            </View>
          </View>
        </>
      )}
    </View>
  )
}

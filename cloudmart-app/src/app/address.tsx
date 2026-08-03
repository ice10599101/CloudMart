import { View, Text, TextInput, ScrollView, TouchableOpacity, Alert, Switch } from 'react-native'
import { useState } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { userApi } from '@/api/user'
import type { Address } from '@/types'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const EMPTY_FORM = { name: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false }

export default function AddressPage() {
  const theme = useTheme()
  const [addresses, setAddresses] = useState<Address[]>([])
  const [loading, setLoading] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...EMPTY_FORM })

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

  useState(() => { loadAddresses() })

  const handleAdd = () => {
    setEditingId(null)
    setForm({ ...EMPTY_FORM })
    setShowForm(true)
  }

  const handleEdit = (addr: Address) => {
    setEditingId(addr.id)
    setForm({ name: addr.name, phone: addr.phone, province: addr.province, city: addr.city, district: addr.district, detail: addr.detail, isDefault: addr.isDefault })
    setShowForm(true)
  }

  const handleDelete = (id: number) => {
    Alert.alert('提示', '确定删除该地址吗？', [
      { text: '取消', style: 'cancel' },
      { text: '确定', style: 'destructive', onPress: async () => {
        try {
          await userApi.deleteAddress(id)
          Alert.alert('成功', '删除成功')
          loadAddresses()
        } catch {
          Alert.alert('错误', '删除失败')
        }
      }},
    ])
  }

  const handleSetDefault = async (id: number) => {
    try {
      await userApi.setDefaultAddress(id)
      Alert.alert('成功', '已设为默认')
      loadAddresses()
    } catch {
      Alert.alert('错误', '设置失败')
    }
  }

  const handleSave = async () => {
    if (!form.name.trim()) { Alert.alert('提示', '请输入收货人'); return }
    if (!form.phone.trim() || !/^1\d{10}$/.test(form.phone)) { Alert.alert('提示', '请输入正确的手机号'); return }
    if (!form.province.trim() || !form.city.trim()) { Alert.alert('提示', '请输入省市区'); return }
    if (!form.detail.trim()) { Alert.alert('提示', '请输入详细地址'); return }

    try {
      if (editingId) {
        await userApi.updateAddress(editingId, form)
      } else {
        await userApi.createAddress(form)
      }
      Alert.alert('成功', '保存成功')
      setShowForm(false)
      loadAddresses()
    } catch {
      Alert.alert('错误', '保存失败')
    }
  }

  const updateForm = (key: string, value: string | boolean) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  if (showForm) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
        <ScrollView contentContainerStyle={{ paddingBottom: Spacing.xxxl }}>
          <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, paddingHorizontal: Spacing.lg }}>
            {[
              { key: 'name', label: '收货人', placeholder: '请输入收货人姓名' },
              { key: 'phone', label: '手机号', placeholder: '请输入手机号' },
              { key: 'province', label: '省份', placeholder: '例如：广东省' },
              { key: 'city', label: '城市', placeholder: '例如：深圳市' },
              { key: 'district', label: '区/县', placeholder: '例如：南山区' },
              { key: 'detail', label: '详细地址', placeholder: '街道、楼牌号等' },
            ].map((field) => (
              <View key={field.key} style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>{field.label}</Text>
                <TextInput
                  value={form[field.key as keyof typeof form] as string}
                  onChangeText={(v) => updateForm(field.key, v)}
                  placeholder={field.placeholder}
                  placeholderTextColor={theme.textTertiary}
                  style={{ fontSize: FontSize.md, color: theme.text, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: theme.bgBase }}
                />
              </View>
            ))}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: Spacing.md }}>
              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>设为默认地址</Text>
              <Switch value={form.isDefault} onValueChange={(v) => updateForm('isDefault', v)} trackColor={{ false: theme.border, true: theme.primary }} thumbColor="#FFFFFF" />
            </View>
          </View>

          <View style={{ flexDirection: 'row', gap: Spacing.md, marginHorizontal: Spacing.lg, marginTop: Spacing.xl }}>
            <TouchableOpacity onPress={() => setShowForm(false)} style={{ flex: 1, height: 48, borderRadius: BorderRadius.xl, borderWidth: 1, borderColor: theme.border, justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary, fontWeight: '500' }}>取消</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={handleSave} style={{ flex: 2, height: 48, borderRadius: BorderRadius.xl, backgroundColor: theme.primary, justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.lg, color: '#FFFFFF', fontWeight: '600' }}>保存</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ paddingBottom: Spacing.xxxl }}>
        {addresses.length === 0 && !loading && (
          <View style={{ alignItems: 'center', paddingVertical: 60 }}>
            <Text style={{ fontSize: 40, marginBottom: Spacing.md, opacity: 0.4 }}>📍</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.textTertiary }}>暂无收货地址</Text>
          </View>
        )}

        {addresses.map((addr) => (
          <View key={addr.id} style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.md, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginBottom: Spacing.xs }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>{addr.name}</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>{addr.phone}</Text>
              {addr.isDefault && (
                <View style={{ paddingHorizontal: 8, paddingVertical: 2, backgroundColor: theme.primary, borderRadius: 4 }}>
                  <Text style={{ fontSize: FontSize.xs, color: '#FFFFFF', fontWeight: '500' }}>默认</Text>
                </View>
              )}
            </View>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 20 }}>
              {addr.province}{addr.city}{addr.district} {addr.detail}
            </Text>
            <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.md, paddingTop: Spacing.md, borderTopWidth: 1, borderTopColor: theme.border }}>
              {!addr.isDefault && (
                <TouchableOpacity onPress={() => handleSetDefault(addr.id)} style={{ paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md }}>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>设为默认</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity onPress={() => handleEdit(addr)} style={{ paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md }}>
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>编辑</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => handleDelete(addr.id)} style={{ paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, borderWidth: 1, borderColor: theme.accentRed, borderRadius: BorderRadius.md }}>
                <Text style={{ fontSize: FontSize.sm, color: theme.accentRed }}>删除</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}

        <TouchableOpacity onPress={handleAdd} style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.lg, height: 48, borderRadius: BorderRadius.lg, borderWidth: 1, borderStyle: 'dashed', borderColor: theme.primary, justifyContent: 'center', alignItems: 'center', backgroundColor: `${theme.primary}10` }}>
          <Text style={{ fontSize: FontSize.lg, color: theme.primary, fontWeight: '500' }}>+ 新增收货地址</Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
  )
}

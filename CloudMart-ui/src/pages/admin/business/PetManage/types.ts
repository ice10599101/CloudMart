import type { PetConfigType } from '@/api/admin/pet'

/** 配置字段描述（同一条描述驱动表格列与表单控件，新增配置类型只加一行描述） */
export interface FieldDef {
  name: string
  label: string
  type?: 'text' | 'textarea' | 'number' | 'switch' | 'select'
  options?: string[]
  required?: boolean
  tip?: string
}

/** 配置类型描述 */
export interface ConfigDef {
  key: PetConfigType
  label: string
  fields: FieldDef[]
}

import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    buyPetItem,
    equipPetItem,
    feedPetItem,
    getPetShop,
    listPetInventory,
    previewPetEquip,
    removePetSkin,
    unequipPetItem,
    wearPetSkin,
    type PetEquipPreview,
    type PetInventoryItem,
    type PetItemType,
} from '@/api/pet'
import { CreamButton, CreamChip } from '@/components/pet-cream/Cream'
import { RARITY_COLOR, RARITY_LABEL } from '../constants'
import styles from '../../PetCream.module.css'

// ---------------- 面板：商城 / 背包 ----------------

export function ShopPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [shop, setShop] = useState<{ balance: number | null; currency: string; items: Array<{
        itemType: PetItemType; code: string; name: string; description: string; icon: string
        rarity: string; priceStarlight: number
    }> } | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getPetShop()
        if (res.success) {
            setShop(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const buy = useCallback(async (item: { itemType: PetItemType; code: string; name: string }) => {
        setBusy(item.code)
        try {
            const { data: res } = await buyPetItem({ itemType: item.itemType, itemCode: item.code })
            if (res.success) {
                message.success(`${item.name} 已入背包`)
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '购买未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!shop) {
        return <Spin />
    }
    return (
        <div>
            <p className={styles.panelDesc}>
                宠物币余额：{shop.balance === null ? '服务暂不可用' : shop.balance} · 共 {shop.items.length} 件在售
            </p>
            <div style={{ paddingTop: 8 }}>
                {shop.items.map(item => (
                    <div key={item.code} className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{item.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {item.name}
                                <CreamChip color={RARITY_COLOR[item.rarity] ?? '#9C8D7E'}>
                                    {RARITY_LABEL[item.rarity] ?? item.rarity}
                                </CreamChip>
                            </p>
                            <p className={styles.panelDesc}>{item.description}</p>
                        </div>
                        <span style={{ fontSize: 13, color: 'var(--brass-600)', whiteSpace: 'nowrap' }}>
                            ⭐ {item.priceStarlight}
                        </span>
                        <CreamButton variant="ghost" loading={busy === item.code}
                            onClick={() => void buy(item)}>购买</CreamButton>
                    </div>
                ))}
            </div>
        </div>
    )
}

export function InventoryPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [list, setList] = useState<PetInventoryItem[] | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    /** 装备替换预览（B12）：按行内「预览」触发，展示替换增量 */
    const [preview, setPreview] = useState<PetEquipPreview | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetInventory()
        if (res.success) {
            setList(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const use = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const run = item.itemType === 'SKIN' ? () => wearPetSkin(item.code) : () => equipPetItem(item.code)
            const { data: res } = await run()
            if (res.success) {
                message.success(`${item.name} 已使用`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '使用未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    /** 卸下：装备按槽位、皮肤整只卸（都会改变属性/外观，需回写宠物状态） */
    const takeOff = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const run = item.itemType === 'SKIN'
                ? () => removePetSkin()
                : () => unequipPetItem(item.slot ?? '')
            const { data: res } = await run()
            if (res.success) {
                message.success(`${item.name} 已卸下`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '卸下未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const showPreview = useCallback(async (item: PetInventoryItem) => {
        setBusy(`preview:${item.code}`)
        try {
            const { data: res } = await previewPetEquip(item.code)
            if (res.success) {
                setPreview(prev => (prev?.itemCode === item.code ? null : res.data))
            } else {
                message.warning(res.error?.message ?? '预览未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    /** 喂养道具（F1）：食物堆叠入包，喂食直接消耗（效果服务端权威，不占免费次数） */
    const feedFood = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const { data: res } = await feedPetItem(item.code)
            if (res.success) {
                message.success(`${item.name} 吃掉了，状态好多了`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '喂食未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            {list.length === 0 ? (
                <p className={styles.panelDesc}>背包空空，去商城逛逛吧</p>
            ) : list.map(item => (
                <div key={item.code}>
                    <div className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{item.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {item.name}
                                <CreamChip color={RARITY_COLOR[item.rarity] ?? '#9C8D7E'}>
                                    {RARITY_LABEL[item.rarity] ?? item.rarity}
                                </CreamChip>
                                {item.equipped ? <CreamChip color="#7E9270">使用中</CreamChip> : null}
                            </p>
                            <p className={styles.panelDesc}>
                                {item.description || item.effect || item.itemType}
                            </p>
                        </div>
                        {item.itemType === 'FOOD' ? (
                            <CreamButton variant="ghost" loading={busy === item.code}
                                onClick={() => void feedFood(item)}>喂食</CreamButton>
                        ) : item.itemType === 'EQUIPMENT' || item.itemType === 'SKIN' ? (
                            <div style={{ display: 'flex', gap: 8 }}>
                                {item.equipped ? (
                                    <CreamButton variant="ghost" loading={busy === item.code}
                                        onClick={() => void takeOff(item)}>卸下</CreamButton>
                                ) : (
                                    <CreamButton variant="ghost" loading={busy === item.code}
                                        onClick={() => void use(item)}>
                                        {item.itemType === 'SKIN' ? '穿戴' : '装备'}
                                    </CreamButton>
                                )}
                                {item.itemType === 'EQUIPMENT' ? (
                                    <CreamButton variant="ghost" loading={busy === `preview:${item.code}`}
                                        onClick={() => void showPreview(item)}>预览</CreamButton>
                                ) : null}
                            </div>
                        ) : null}
                    </div>
                    {preview && preview.itemCode === item.code ? (
                        <p className={styles.panelDesc} style={{ paddingBottom: 8 }}>
                            替换后：HP {preview.after.maxHp}（{preview.delta.maxHp >= 0 ? '+' : ''}
                            {preview.delta.maxHp}）· 力 {preview.after.strength}（{preview.delta.strength >= 0 ? '+' : ''}
                            {preview.delta.strength}）· 智 {preview.after.intelligence}（{preview.delta.intelligence >= 0 ? '+' : ''}
                            {preview.delta.intelligence}）· 敏 {preview.after.agility}（{preview.delta.agility >= 0 ? '+' : ''}
                            {preview.delta.agility}）· 魅 {preview.after.charm}（{preview.delta.charm >= 0 ? '+' : ''}
                            {preview.delta.charm}）
                        </p>
                    ) : null}
                </div>
            ))}
        </div>
    )
}

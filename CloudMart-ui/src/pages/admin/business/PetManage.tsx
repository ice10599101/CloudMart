import { Card, Tabs } from 'antd'
import { CONFIGS } from './PetManage/constants'
import { ConfigPanel } from './PetManage/panels/config'
import { DashboardPanel } from './PetManage/panels/dashboard'
import { GovernancePanel } from './PetManage/panels/governance'
import { PersonaPhrasePanel } from './PetManage/panels/persona-phrase'
import { PassClaimPanel } from './PetManage/panels/pass-claim'
import { QuestReceiptPanel } from './PetManage/panels/quest-receipt'
import { ReportPanel } from './PetManage/panels/report'
import { SeasonPanel } from './PetManage/panels/season'
import { SensitiveWordPanel } from './PetManage/panels/sensitive-word'
import { UserPanel } from './PetManage/panels/user'
import { UserProgressPanel } from './PetManage/panels/user-progress'
import { WallPanel } from './PetManage/panels/wall'

/**
 * 宠物运营后台（社区宠物模块三期）。
 *
 * 三个主域：数据看板（运营指标与趋势）/ 配置管理（岗位·课程·装备·皮肤·技能·进化·活动·职业·家具·每日任务，
 * 统一"带 id 为更新"的通用 CRUD）/ 留言审核（隐藏·恢复·删除，保留审计）；
 * 另有举报闭环、相册治理、用户宠物调账、赛季、敏感词库、口头禅、任务回执等运营面板。
 * 面板按域拆在 ./PetManage/panels/，字段描述在 ./PetManage/constants.ts。
 * 图表沿用工程既有做法（无图表库，用 div 自绘柱状），避免引入新依赖。
 */
export default function PetManage() {
  return (
    <Card title="宠物运营" bodyStyle={{ paddingTop: 8 }}>
      <Tabs
        items={[
          { key: 'dashboard', label: '数据看板', children: <DashboardPanel /> },
          {
            key: 'configs',
            label: '配置管理',
            children: (
              <Tabs
                type="card"
                size="small"
                items={CONFIGS.map((def) => ({
                  key: def.key,
                  label: def.label,
                  children: <ConfigPanel def={def} />,
                }))}
              />
            ),
          },
          { key: 'wall', label: '留言审核', children: <WallPanel /> },
          { key: 'reports', label: '举报处理', children: <ReportPanel /> },
          { key: 'album-governance', label: '相册·治理', children: <GovernancePanel /> },
          { key: 'users', label: '用户宠物', children: <UserPanel /> },
          { key: 'user-progress', label: '进度查询', children: <UserProgressPanel /> },
          { key: 'seasons', label: '赛季管理', children: <SeasonPanel /> },
          { key: 'pass-claims', label: '通行证补发', children: <PassClaimPanel /> },
          { key: 'sensitive-words', label: '敏感词库', children: <SensitiveWordPanel /> },
          { key: 'persona-phrases', label: '口头禅', children: <PersonaPhrasePanel /> },
          { key: 'quest-receipts', label: '任务回执', children: <QuestReceiptPanel /> },
        ]}
      />
    </Card>
  )
}

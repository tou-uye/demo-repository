import { Card, Statistic, Row, Col, Button, message as antdMessage } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

export default function Overview() {
  const [data, setData] = useState<{ unreadMessages: number; pendingReports: number; totalAssetUsd: number }>({ unreadMessages: 0, pendingReports: 0, totalAssetUsd: 0 })
  useEffect(() => {
    fetchJson<typeof data>('/api/overview').then(r => r && setData({
      unreadMessages: Number((r as any).unreadMessages ?? 0),
      pendingReports: Number((r as any).pendingReports ?? 0),
      totalAssetUsd: Number((r as any).totalAssetUsd ?? 0)
    }))
  }, [])
  const collect = async () => {
    await fetch('/api/messages/collect', { method: 'POST' })
    antdMessage.success('已触发采集与分析')
    const r = await fetchJson<typeof data>('/api/overview')
    r && setData({
      unreadMessages: Number((r as any).unreadMessages ?? 0),
      pendingReports: Number((r as any).pendingReports ?? 0),
      totalAssetUsd: Number((r as any).totalAssetUsd ?? 0)
    })
  }
  return (
    <Row gutter={16}>
      <Col span={8}><Card extra={<Button onClick={collect} type="primary">采集并分析</Button>}><Statistic title="未读消息数" value={data.unreadMessages} /></Card></Col>
      <Col span={8}><Card><Statistic title="待审核报告数" value={data.pendingReports} /></Card></Col>
      <Col span={8}><Card><Statistic title="当前总资产估值" value={data.totalAssetUsd} prefix="$" /></Card></Col>
    </Row>
  )
}

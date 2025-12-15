import { Card, Statistic, Row, Col, Button, Space, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'
import { useNavigate } from 'react-router-dom'

type OverviewResp = { unreadMessages: number; pendingReports: number; totalAssetUsd: number }
type CollectStatus = { status?: string; running?: boolean; finishedAt?: string; startedAt?: string }

export default function Overview() {
  const navigate = useNavigate()
  const [data, setData] = useState<OverviewResp>({ unreadMessages: 0, pendingReports: 0, totalAssetUsd: 0 })
  const [collectStatus, setCollectStatus] = useState<CollectStatus>({})

  const load = async () => {
    const ov = await fetchJson<OverviewResp>('/api/overview')
    if (ov) {
      setData({
        unreadMessages: Number((ov as any).unreadMessages ?? 0),
        pendingReports: Number((ov as any).pendingReports ?? 0),
        totalAssetUsd: Number((ov as any).totalAssetUsd ?? 0)
      })
    }
    const cs = await fetchJson<any>('/api/messages/collect/status')
    if (cs) {
      setCollectStatus({
        status: cs.status,
        running: Boolean(cs.running),
        finishedAt: cs.finishedAt,
        startedAt: cs.startedAt
      })
    }
  }

  useEffect(() => {
    load()
  }, [])

  const renderCollectStatus = () => {
    if (!collectStatus.status) return null
    const color = collectStatus.running ? 'processing' : collectStatus.status === 'FAILED' ? 'error' : 'default'
    const time = collectStatus.finishedAt || collectStatus.startedAt
    return (
      <Space size={8} style={{ marginTop: 8 }}>
        <Tag color={color}>采集状态：{collectStatus.status}</Tag>
        {time && <Typography.Text type="secondary">时间：{time}</Typography.Text>}
      </Space>
    )
  }

  return (
    <Row gutter={16}>
      <Col span={8}>
        <Card>
          <Statistic title="未读消息" value={data.unreadMessages} />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/messages')}>去查看</Button>
          </Space>
          {renderCollectStatus()}
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic title="待审核报告" value={data.pendingReports} />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/reports')}>去查看</Button>
          </Space>
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic title="总资产(USD)" value={data.totalAssetUsd} prefix="$" />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/positions')}>去查看</Button>
          </Space>
        </Card>
      </Col>
    </Row>
  )
}

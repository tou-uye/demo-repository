import { Card, Statistic, Row, Col, Button, message as antdMessage, Space } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'
import { useNavigate } from 'react-router-dom'

export default function Overview() {
  const navigate = useNavigate()
  const [data, setData] = useState<{ unreadMessages: number; pendingReports: number; totalAssetUsd: number }>({ unreadMessages: 0, pendingReports: 0, totalAssetUsd: 0 })

  useEffect(() => {
    fetchJson<typeof data>('/api/overview').then(r => r && setData({
      unreadMessages: Number((r as any).unreadMessages ?? 0),
      pendingReports: Number((r as any).pendingReports ?? 0),
      totalAssetUsd: Number((r as any).totalAssetUsd ?? 0)
    }))
  }, [])

  const collect = async () => {
    const res = await fetch('/api/messages/collect', { method: 'POST' })
    const txt = await res.text()
    let started = true
    try {
      const json = JSON.parse(txt)
      started = Boolean(json?.started ?? true)
    } catch {}
    const key = 'collect'
    antdMessage.open({ key, type: 'loading', content: started ? '采集任务已启动，正在运行…' : '采集任务正在运行…', duration: 0 })
    const poll = async () => {
      const status = await fetchJson<any>('/api/messages/collect/status')
      if (!status || status.running) return false
      return true
    }
    const timer = window.setInterval(async () => {
      const done = await poll()
      if (!done) return
      window.clearInterval(timer)
      const r = await fetchJson<typeof data>('/api/overview')
      r && setData({
        unreadMessages: Number((r as any).unreadMessages ?? 0),
        pendingReports: Number((r as any).pendingReports ?? 0),
        totalAssetUsd: Number((r as any).totalAssetUsd ?? 0)
      })
      antdMessage.open({ key, type: 'success', content: '采集完成，已刷新', duration: 2 })
    }, 1500)
  }

  return (
    <Row gutter={16}>
      <Col span={8}>
        <Card extra={<Button onClick={collect} type="primary">采集并分析</Button>}>
          <Statistic title="未读消息数" value={data.unreadMessages} />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/messages')}>查看消息</Button>
          </Space>
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic title="待审核报告数" value={data.pendingReports} />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/reports')}>查看报告</Button>
          </Space>
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic title="当前总资产估值" value={data.totalAssetUsd} prefix="$" />
          <Space style={{ marginTop: 12 }}>
            <Button size="small" onClick={() => navigate('/positions')}>查看持仓</Button>
          </Space>
        </Card>
      </Col>
    </Row>
  )
}

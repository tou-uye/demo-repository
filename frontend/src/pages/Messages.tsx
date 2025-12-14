import { Card, List, Tag, Button, message as antdMessage, Select, Space, Typography, Modal, Badge, Divider } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

type Msg = { id: number; title: string; symbol?: string; sentiment?: string; sourceUrl?: string; content?: string; summary?: string; impactDescription?: string; read?: boolean }

export default function Messages() {
  const [data, setData] = useState<Msg[]>([])
  const [loading, setLoading] = useState(false)
  const [symbolFilter, setSymbolFilter] = useState<string | undefined>(undefined)
  const [sentimentFilter, setSentimentFilter] = useState<string | undefined>(undefined)
  const [selected, setSelected] = useState<Msg | null>(null)

  useEffect(() => {
    fetchJson<Msg[]>('/api/messages').then(r => setData(r ?? []))
  }, [])

  const markRead = async (ids: number[]) => {
    if (!ids.length) return
    await fetch('/api/messages/read', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ids)
    })
    setData(prev => prev.map(m => ids.includes(m.id) ? { ...m, read: true } : m))
    antdMessage.success('已标记已读')
  }

  const collect = async () => {
    setLoading(true)
    try {
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
        const r = await fetchJson<Msg[]>('/api/messages')
        setData(r ?? [])
        antdMessage.open({ key, type: 'success', content: '采集完成，已刷新', duration: 2 })
        setLoading(false)
      }, 1500)
    } finally {
      // keep loading until poll done
    }
  }

  const symbols = Array.from(new Set((data ?? []).map(i => i.symbol).filter(Boolean))) as string[]
  const filtered = data.filter(item => {
    const matchSymbol = symbolFilter ? item.symbol === symbolFilter : true
    const matchSentiment = sentimentFilter ? item.sentiment === sentimentFilter : true
    return matchSymbol && matchSentiment
  })
  const unreadCount = (data ?? []).filter(item => !item.read).length

  return (
    <Card
      title="消息列表"
      extra={
        <Space size={10} wrap>
          <Tag color="red">未读 {unreadCount}</Tag>
          <Typography.Text type="secondary">筛选提示：情绪仅限“利好/利空/中性”，币种按消息标注</Typography.Text>
          <Button size="small" onClick={() => markRead(filtered.map(i => i.id))} disabled={!filtered.some(i => !i.read)}>
            标记全部已读
          </Button>
          <Select
            allowClear
            placeholder="按币种筛选"
            style={{ width: 140 }}
            value={symbolFilter}
            onChange={setSymbolFilter}
            options={symbols.map(s => ({ value: s, label: s }))}
          />
          <Select
            allowClear
            placeholder="按情绪筛选"
            style={{ width: 140 }}
            value={sentimentFilter}
            onChange={setSentimentFilter}
            options={[
              { value: '利好', label: '利好' },
              { value: '利空', label: '利空' },
              { value: '中性', label: '中性' }
            ]}
          />
          <Button type="primary" onClick={collect} loading={loading}>采集并分析</Button>
        </Space>
      }
    >
      <List
        dataSource={filtered}
        rowKey={(item, idx) => String(item?.id ?? idx)}
        renderItem={(item) => (
          <List.Item onClick={() => setSelected(item)} style={{ cursor: 'pointer', opacity: item.read ? 0.7 : 1 }}>
            <List.Item.Meta
              title={
                <Space size={8}>
                  <Badge dot={!item.read} offset={[0, 2]}>
                    <Typography.Text strong>{item.title}</Typography.Text>
                  </Badge>
                  {item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">来源</a>}
                </Space>
              }
              description={item.symbol}
            />
            {item.sentiment && (
              <Tag color={item.sentiment === '利好' ? 'green' : item.sentiment === '利空' ? 'red' : 'default'}>
                {item.sentiment}
              </Tag>
            )}
          </List.Item>
        )}
      />
      <Modal
        open={!!selected}
        onCancel={() => setSelected(null)}
        footer={
          <Space>
            <Button onClick={() => setSelected(null)}>关闭</Button>
            {selected?.id != null && <Button type="primary" onClick={() => { markRead([selected.id]); setSelected(null) }}>标记已读</Button>}
          </Space>
        }
        title="消息详情"
      >
        <Space direction="vertical" size={8}>
          <Typography.Text strong>{selected?.title}</Typography.Text>
          <Typography.Text type="secondary">币种：{selected?.symbol || '-'}</Typography.Text>
          <Typography.Text type="secondary">情绪：{selected?.sentiment || '-'}</Typography.Text>
          {selected?.sourceUrl && <a href={selected.sourceUrl} target="_blank" rel="noreferrer">查看来源</a>}
          {(selected?.summary || selected?.impactDescription || selected?.content) && <Divider style={{ margin: '8px 0' }} />}
          {selected?.summary && (
            <div>
              <Typography.Text type="secondary">摘要</Typography.Text>
              <div style={{ whiteSpace: 'pre-wrap' }}>{selected.summary}</div>
            </div>
          )}
          {selected?.impactDescription && (
            <div>
              <Typography.Text type="secondary">影响描述</Typography.Text>
              <div style={{ whiteSpace: 'pre-wrap' }}>{selected.impactDescription}</div>
            </div>
          )}
          {selected?.content && (
            <div>
              <Typography.Text type="secondary">正文</Typography.Text>
              <div style={{ whiteSpace: 'pre-wrap', maxHeight: 240, overflow: 'auto', border: '1px solid #f0f0f0', padding: 8, borderRadius: 6 }}>
                {selected.content}
              </div>
            </div>
          )}
        </Space>
      </Modal>
    </Card>
  )
}

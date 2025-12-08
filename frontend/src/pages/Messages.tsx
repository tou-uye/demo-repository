import { Card, List, Tag, Button, message as antdMessage, Select, Space, Typography, Modal } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

type Msg = { id: number; title: string; symbol?: string; sentiment?: string; sourceUrl?: string }

export default function Messages() {
  const [data, setData] = useState<Msg[]>([])
  const [loading, setLoading] = useState(false)
  const [symbolFilter, setSymbolFilter] = useState<string | undefined>(undefined)
  const [sentimentFilter, setSentimentFilter] = useState<string | undefined>(undefined)

  useEffect(() => {
    fetchJson<Msg[]>('/api/messages').then(r => setData(r ?? []))
  }, [])

  const collect = async () => {
    setLoading(true)
    try {
      await fetch('/api/messages/collect', { method: 'POST' })
      antdMessage.success('已触发采集并分析')
      const r = await fetchJson<Msg[]>('/api/messages')
      setData(r ?? [])
    } finally {
      setLoading(false)
    }
  }

  const symbols = Array.from(new Set((data ?? []).map(i => i.symbol).filter(Boolean))) as string[]
  const filtered = data.filter(item => {
    const matchSymbol = symbolFilter ? item.symbol === symbolFilter : true
    const matchSentiment = sentimentFilter ? item.sentiment === sentimentFilter : true
    return matchSymbol && matchSentiment
  })
  const [selected, setSelected] = useState<Msg | null>(null)

  return (
    <Card
      title="消息列表"
      extra={
        <Space>
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
            placeholder="按情感筛选"
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
          <List.Item onClick={() => setSelected(item)} style={{ cursor: 'pointer' }}>
            <List.Item.Meta
              title={<Space size={8}><Typography.Text strong>{item.title}</Typography.Text>{item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">来源</a>}</Space>}
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
      <Modal open={!!selected} onCancel={() => setSelected(null)} footer={null} title="消息详情">
        <Space direction="vertical" size={8}>
          <Typography.Text strong>{selected?.title}</Typography.Text>
          <Typography.Text type="secondary">币种：{selected?.symbol || '-'}</Typography.Text>
          <Typography.Text type="secondary">情感：{selected?.sentiment || '-'}</Typography.Text>
          {selected?.sourceUrl && <a href={selected.sourceUrl} target="_blank" rel="noreferrer">查看来源</a>}
        </Space>
      </Modal>
    </Card>
  )
}

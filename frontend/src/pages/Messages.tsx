import { Card, List, Tag, Button, message as antdMessage } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

type Msg = { id: number; title: string; symbol?: string; sentiment?: string; sourceUrl?: string }

export default function Messages() {
  const [data, setData] = useState<Msg[]>([])
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    fetchJson<Msg[]>('/api/messages').then(r => setData(r ?? []))
  }, [])
  const collect = async () => {
    setLoading(true)
    try {
      await fetch('/api/messages/collect', { method: 'POST' })
      antdMessage.success('已触发采集与分析')
      const r = await fetchJson<Msg[]>('/api/messages')
      setData(r ?? [])
    } finally {
      setLoading(false)
    }
  }
  return (
    <Card title="消息列表" extra={<Button type="primary" onClick={collect} loading={loading}>采集并分析</Button>}>
      <List
        dataSource={data}
        rowKey={(item, idx) => String(item?.id ?? idx)}
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta title={item.title} description={item.symbol} />
            {item.sentiment && <Tag color={item.sentiment === '利好' ? 'green' : item.sentiment === '利空' ? 'red' : 'default'}>{item.sentiment}</Tag>}
          </List.Item>
        )}
      />
    </Card>
  )
}

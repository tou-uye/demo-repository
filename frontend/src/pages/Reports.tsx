import { Card, Button, List, message as antdMessage } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

type Report = { id: number; summary: string; status: string }

export default function Reports() {
  const [data, setData] = useState<Report[] | null>([])
  const load = () => fetchJson<Report[]>('/api/reports/pending').then(r => setData(Array.isArray(r) ? r : []))
  useEffect(() => { load() }, [])
  const approve = (id?: number) => id != null && fetch(`/api/review/approve/${id}`, { method: 'POST' }).then(() => { antdMessage.success('已通过'); load() })
  const reject = (id?: number) => id != null && fetch(`/api/review/reject/${id}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: '不符合' }) }).then(() => { antdMessage.info('已驳回'); load() })
  const safeData = Array.isArray(data) ? data : []
  return (
    <Card title="建议报告">
      <List
        dataSource={safeData}
        rowKey={(item, idx) => String(item?.id ?? idx)}
        renderItem={(item, idx) => (
          <List.Item actions={[<Button key="approve" type="primary" onClick={() => approve(item?.id)}>通过</Button>, <Button key="reject" onClick={() => reject(item?.id)}>驳回</Button>] }>
            <List.Item.Meta title={`报告 #${item?.id ?? idx}`} description={item?.summary ?? ''} />
          </List.Item>
        )}
      />
    </Card>
  )
}

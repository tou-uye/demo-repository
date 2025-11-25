import { Card, Table } from 'antd'
import { useEffect, useState } from 'react'
import { fetchJson } from '../utils/api'

type Position = { id: number; symbol: string; percent: number; amountUsd: number }

export default function Positions() {
  const [data, setData] = useState<Position[]>([])
  useEffect(() => {
    fetchJson<Position[]>('/api/positions/current').then(r => setData(r ?? []))
  }, [])
  return (
    <Card title="持仓数据">
      <Table dataSource={data} pagination={false} rowKey={(r, idx) => String(r?.id ?? idx)} columns={[
        { title: '币种', dataIndex: 'symbol' },
        { title: '占比(%)', dataIndex: 'percent' },
        { title: '金额(USD)', dataIndex: 'amountUsd' }
      ]} />
    </Card>
  )
}

import { Card, Table, Button, Space, Typography, Tag } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { fetchJson } from '../utils/api'

type Position = { id?: number; symbol: string; percent: number; amountUsd: number; createdAt?: string }
type SnapshotPoint = { date: string; totalUsd: number }
type SnapshotSeries = { symbol: string; points: SnapshotPoint[] }
type SnapshotResp = { totals: SnapshotPoint[]; series: SnapshotSeries[] }

const colors = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#13c2c2', '#722ed1']

export default function Positions() {
  const [current, setCurrent] = useState<Position[]>([])
  const [snapshots, setSnapshots] = useState<SnapshotPoint[]>([])
  const [series, setSeries] = useState<SnapshotSeries[]>([])
  const [activeSymbol, setActiveSymbol] = useState<string>('TOTAL')

  useEffect(() => {
    fetchJson<Position[]>('/api/positions/current').then(r => setCurrent(Array.isArray(r) ? r : []))
    fetchJson<SnapshotResp>('/api/positions/snapshots?days=7').then(r => {
      if (r && (r as any).totals) {
        setSnapshots(r.totals ?? [])
        setSeries(r.series ?? [])
      } else {
        setSnapshots([])
        setSeries([])
      }
    })
  }, [])

  const total = useMemo(() => current.reduce((s, i) => s + Number(i.amountUsd || 0), 0), [current])

  const pieStyle = useMemo(() => {
    if (!current.length || total <= 0) return ''
    let acc = 0
    const segments = current.map((item, idx) => {
      const pct = (Number(item.amountUsd || 0) / total) * 100
      const start = acc
      acc += pct
      const end = acc
      return `${colors[idx % colors.length]} ${start}% ${end}%`
    })
    return `conic-gradient(${segments.join(',')})`
  }, [current, total])

  const groupedHistory = useMemo(() => {
    return snapshots.map(s => [s.date, Number(s.totalUsd || 0)]) as [string, number][]
  }, [snapshots])

  const activeSeries = useMemo(() => {
    if (activeSymbol === 'TOTAL') return groupedHistory
    const found = series.find(s => s.symbol === activeSymbol)
    if (!found) return []
    return found.points.map(p => [p.date, Number(p.totalUsd || 0)]) as [string, number][]
  }, [activeSymbol, series, groupedHistory])

  const exportCsv = () => {
    window.open('/api/positions/export', '_blank')
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title="当前持仓"
        extra={<Button onClick={exportCsv}>导出 Excel</Button>}
      >
        <Space align="start" size="large" style={{ width: '100%', flexWrap: 'wrap' }}>
          <div style={{ width: 220, height: 220, borderRadius: '50%', background: pieStyle || '#f5f5f5', position: 'relative', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
            <div style={{ position: 'absolute', inset: 30, borderRadius: '50%', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column' }}>
              <Typography.Text type="secondary">总额(USD)</Typography.Text>
              <Typography.Title level={4} style={{ margin: 0 }}>{total.toLocaleString()}</Typography.Title>
            </div>
          </div>
          <div style={{ flex: 1, minWidth: 320 }}>
            {current.map((item, idx) => (
              <Space key={idx} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <Space>
                  <span style={{ display: 'inline-block', width: 12, height: 12, borderRadius: 3, background: colors[idx % colors.length] }} />
                  <Typography.Text strong>{item.symbol}</Typography.Text>
                </Space>
                <Space>
                  <Tag color="blue">{item.percent}%</Tag>
                  <Typography.Text>${Number(item.amountUsd || 0).toLocaleString()}</Typography.Text>
                </Space>
              </Space>
            ))}
          </div>
        </Space>
        <Table
          style={{ marginTop: 16 }}
          dataSource={current}
          pagination={false}
          rowKey={(r, idx) => String(r?.id ?? idx)}
          columns={[
            { title: '币种', dataIndex: 'symbol' },
            { title: '占比(%)', dataIndex: 'percent' },
            { title: '金额(USD)', dataIndex: 'amountUsd', render: (v: number) => Number(v || 0).toLocaleString() }
          ]}
        />
      </Card>

      <Card title="近7日持仓趋势" extra={
        <Space size={8} wrap>
          <Button type={activeSymbol === 'TOTAL' ? 'primary' : 'default'} onClick={() => setActiveSymbol('TOTAL')}>总额</Button>
          {series.map(s => (
            <Button key={s.symbol} type={activeSymbol === s.symbol ? 'primary' : 'default'} onClick={() => setActiveSymbol(s.symbol)}>
              {s.symbol}
            </Button>
          ))}
        </Space>
      }>
        <TrendLine data={activeSeries} />
      </Card>
    </Space>
  )
}

function TrendLine({ data }: { data: [string, number][] }) {
  if (!data.length) return <Typography.Text type="secondary">暂无历史数据</Typography.Text>
  const maxVal = Math.max(...data.map(([, v]) => v))
  const minVal = Math.min(...data.map(([, v]) => v))
  const padding = 10
  const width = 600
  const height = 200
  const points = data.map(([_, v], idx) => {
    const x = (idx / Math.max(data.length - 1, 1)) * (width - padding * 2) + padding
    const y = height - padding - ((v - minVal) / Math.max(maxVal - minVal || 1, 1)) * (height - padding * 2)
    return `${x},${y}`
  }).join(' ')
  return (
    <svg width="100%" viewBox={`0 0 ${width} ${height}`}>
      <polyline fill="none" stroke="#1677ff" strokeWidth="2" points={points} />
      {data.map(([label], idx) => {
        const x = (idx / Math.max(data.length - 1, 1)) * (width - padding * 2) + padding
        const y = height - padding - ((data[idx][1] - minVal) / Math.max(maxVal - minVal || 1, 1)) * (height - padding * 2)
        return (
          <g key={label}>
            <circle cx={x} cy={y} r={3} fill="#1677ff" />
            <text x={x} y={height - 2} fontSize="10" textAnchor="middle">{label.slice(5)}</text>
          </g>
        )
      })}
    </svg>
  )
}

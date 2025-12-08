import { Card, Button, List, message as antdMessage, Modal, Input, Space, Typography, Tag, Collapse } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { fetchJson } from '../utils/api'

type Report = {
  id: number; summary: string; status: string; messageId?: number;
  planJson?: string; analysisJson?: string; positionsSnapshotJson?: string; adjustmentsJson?: string;
  riskNotes?: string; confidence?: string; sentiment?: string; impactStrength?: string; keyPoints?: string;
}
type Msg = { id: number; title: string; symbol?: string; sentiment?: string; sourceUrl?: string; createdAt?: string }

export default function Reports() {
  const [data, setData] = useState<Report[] | null>([])
  const [messages, setMessages] = useState<Msg[]>([])
  const [rejecting, setRejecting] = useState<{ id: number; open: boolean }>({ id: -1, open: false })
  const [reason, setReason] = useState('')
  const [status, setStatus] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('PENDING')

  const load = () => {
    // 统一取全量，前端再过滤，避免后端 query 未命中导致空列表
    fetchJson<Report[]>('/api/reports?status=ALL').then(r => setData(Array.isArray(r) ? r : []))
  }

  useEffect(() => { load(); fetchJson<Msg[]>('/api/messages').then(r => setMessages(r ?? [])) }, [status])

  const approve = (id?: number) => id != null && fetch(`/api/review/approve/${id}`, { method: 'POST' }).then(() => { antdMessage.success('已通过'); load() })

  const reject = (id?: number) => {
    if (id == null) return
    setRejecting({ id, open: true })
    setReason('')
  }

  const submitReject = () => {
    const id = rejecting.id
    fetch(`/api/review/reject/${id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason })
    }).then(() => { antdMessage.info('已驳回'); setRejecting({ id: -1, open: false }); load() })
  }

  const msgMap = useMemo(() => {
    const map: Record<string, Msg> = {}
    messages.forEach(m => { if (m.id != null) map[String(m.id)] = m })
    return map
  }, [messages])

  const safeData = useMemo(() => {
    const list = Array.isArray(data) ? data : []
    if (status === 'ALL') return list
    return list.filter(item => (item.status ?? '').toUpperCase() === status)
  }, [data, status])

  return (
    <>
      <Card title="建议报告" extra={
        <Space size={8}>
          <Button type={status === 'PENDING' ? 'primary' : 'default'} onClick={() => setStatus('PENDING')}>待审核</Button>
          <Button type={status === 'APPROVED' ? 'primary' : 'default'} onClick={() => setStatus('APPROVED')}>已通过</Button>
          <Button type={status === 'REJECTED' ? 'primary' : 'default'} onClick={() => setStatus('REJECTED')}>已驳回</Button>
          <Button type={status === 'ALL' ? 'primary' : 'default'} onClick={() => setStatus('ALL')}>全部</Button>
        </Space>
      }>
        <List
          dataSource={safeData}
          rowKey={(item, idx) => String(item?.id ?? idx)}
          renderItem={(item, idx) => {
            const msg = msgMap[String(item.messageId ?? '')]
            const plan = tryParse(item.planJson)
            const pos = tryParse(item.positionsSnapshotJson)
            const adj = tryParse(item.adjustmentsJson)
            const analysis = tryParse(item.analysisJson)
            return (
              <List.Item
                actions={[
                  <Button key="approve" type="primary" disabled={item.status !== 'PENDING'} onClick={() => approve(item?.id)}>通过</Button>,
                  <Button key="reject" disabled={item.status !== 'PENDING'} onClick={() => reject(item?.id)}>驳回</Button>
                ]}
              >
                  <List.Item.Meta
                    title={`报告 #${item?.id ?? idx}`}
                    description={
                      <Space direction="vertical" size={4}>
                        <Typography.Text>{item?.summary ?? ''}</Typography.Text>
                        {msg && (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            原始消息：{msg.title} {msg.symbol && `(${msg.symbol})`} {msg.sentiment && `· ${msg.sentiment}`} {msg.sourceUrl && <a href={msg.sourceUrl} target="_blank" rel="noreferrer">来源</a>}
                          </Typography.Text>
                        )}
                        <Space size={8} wrap>
                          <Tag color={item.status === 'PENDING' ? 'blue' : item.status === 'APPROVED' ? 'green' : 'red'}>状态 {item.status}</Tag>
                          {item.sentiment && <Tag color={item.sentiment === '利好' ? 'green' : item.sentiment === '利空' ? 'red' : 'default'}>情感 {item.sentiment}</Tag>}
                          {item.impactStrength && <Tag color="blue">影响 {item.impactStrength}</Tag>}
                          {item.confidence && <Tag color="purple">信心 {item.confidence}</Tag>}
                          {item.reviewReason && item.status === 'REJECTED' && <Tag color="volcano">驳回原因：{item.reviewReason}</Tag>}
                        </Space>
                        {(plan || pos || adj || analysis || item.riskNotes || item.keyPoints) && (
                          <Collapse ghost size="small" items={[
                            {
                              key: 'detail',
                              label: '查看详情',
                              children: (
                                <Space direction="vertical" size={6} style={{ width: '100%' }}>
                                  {item.keyPoints && <Typography.Text>要点：{item.keyPoints}</Typography.Text>}
                                  {item.riskNotes && <Typography.Text>风险：{item.riskNotes}</Typography.Text>}
                                  {analysis && <PreBlock title="分析" data={analysis} />}
                                  {pos && <PreBlock title="持仓快照" data={pos} />}
                                  {adj && <PreBlock title="调整建议" data={adj} />}
                                  {plan && <PreBlock title="原始Plan" data={plan} />}
                                </Space>
                              )
                            }
                          ]} />
                        )}
                      </Space>
                    }
                  />
                </List.Item>
            )
          }}
        />
      </Card>

      <Modal
        title="驳回原因"
        open={rejecting.open}
        onOk={submitReject}
        onCancel={() => setRejecting({ id: -1, open: false })}
        okText="提交"
        cancelText="取消"
      >
        <Input.TextArea rows={4} value={reason} onChange={e => setReason(e.target.value)} placeholder="请输入驳回原因" />
      </Modal>
    </>
  )
}

function tryParse(json?: string) {
  if (!json) return null
  try { return JSON.parse(json) } catch { return null }
}

function PreBlock({ title, data }: { title: string; data: any }) {
  return (
    <div style={{ background: '#f5f5f5', padding: 8, borderRadius: 6 }}>
      <Typography.Text strong>{title}</Typography.Text>
      <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{typeof data === 'string' ? data : JSON.stringify(data, null, 2)}</pre>
    </div>
  )
}

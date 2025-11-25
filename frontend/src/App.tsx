import { Layout, Menu } from 'antd'
import { LaptopOutlined, DatabaseOutlined, FileTextOutlined, HomeOutlined } from '@ant-design/icons'
import { Link, Route, Routes } from 'react-router-dom'
import Messages from './pages/Messages'
import Positions from './pages/Positions'
import Reports from './pages/Reports'
import Overview from './pages/Overview'

export default function App() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider breakpoint="lg" collapsedWidth={0}>
        <div style={{ color: '#fff', padding: 16, fontWeight: 600 }}>AI Invest</div>
        <Menu theme="dark" mode="inline" items={[
          { key: 'overview', icon: <HomeOutlined />, label: <Link to="/">系统概览</Link> },
          { key: 'messages', icon: <LaptopOutlined />, label: <Link to="/messages">消息列表</Link> },
          { key: 'positions', icon: <DatabaseOutlined />, label: <Link to="/positions">持仓数据</Link> },
          { key: 'reports', icon: <FileTextOutlined />, label: <Link to="/reports">建议报告</Link> }
        ]} />
      </Layout.Sider>
      <Layout>
        <Layout.Header style={{ background: '#fff' }} />
        <Layout.Content style={{ margin: '24px' }}>
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/messages" element={<Messages />} />
            <Route path="/positions" element={<Positions />} />
            <Route path="/reports" element={<Reports />} />
          </Routes>
        </Layout.Content>
      </Layout>
    </Layout>
  )
}


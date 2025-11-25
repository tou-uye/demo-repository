import React from 'react'

type Props = { children: React.ReactNode }
type State = { hasError: boolean }

export default class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) { super(props); this.state = { hasError: false } }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch() {}
  render() { return this.state.hasError ? <div style={{ padding: 24 }}>页面出现异常</div> : this.props.children }
}


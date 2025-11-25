export async function fetchJson<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url)
    const txt = await res.text()
    try { return JSON.parse(txt) as T } catch { return null }
  } catch { return null }
}


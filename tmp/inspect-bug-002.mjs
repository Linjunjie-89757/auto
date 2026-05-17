const login = await fetch('http://127.0.0.1:8080/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Workspace-Code': 'ALL',
  },
  body: JSON.stringify({ username: 'zhangli', password: '123456' }),
})

const cookie = login.headers.get('set-cookie')
if (!cookie) {
  throw new Error('No login cookie returned')
}

const bugsResponse = await fetch('http://127.0.0.1:8080/api/bugs', {
  headers: {
    Cookie: cookie,
    'X-Workspace-Code': 'ALL',
  },
})
const bugsPayload = await bugsResponse.json()
const bug = bugsPayload.data.items.find((item) => item.bugNo === 'BUG-002')
if (!bug) {
  throw new Error('BUG-002 not found')
}

const detailResponse = await fetch(`http://127.0.0.1:8080/api/bugs/${bug.id}`, {
  headers: {
    Cookie: cookie,
    'X-Workspace-Code': bug.workspaceCode || 'trade-core',
  },
})
const detailPayload = await detailResponse.json()

console.log(JSON.stringify({
  bug,
  detail: detailPayload.data,
}, null, 2))

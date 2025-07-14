import { NextRequest, NextResponse } from 'next/server'

const MOPPY_API_BASE = process.env.MOPPY_API_URL || 'http://localhost:8080'

export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ path: string[] }> }
) {
    try {
        const { path } = await params
        const apiPath = path.join('/')
        const url = `${MOPPY_API_BASE}/api/${apiPath}`

        console.log(`Proxying GET request to: ${url}`)

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        })

        const data = await response.text()

        if (!response.ok) {
            return NextResponse.json(
                { error: `API Error: ${data}`, status: response.status },
                { status: response.status }
            )
        }

        try {
            const jsonData = JSON.parse(data)
            return NextResponse.json(jsonData)
        } catch {
            return new NextResponse(data, {
                status: response.status,
                headers: { 'Content-Type': response.headers.get('Content-Type') || 'text/plain' }
            })
        }
    } catch (error) {
        console.error('Proxy error:', error)
        return NextResponse.json(
            { error: 'Failed to connect to Moppy API server', details: error instanceof Error ? error.message : 'Unknown error' },
            { status: 503 }
        )
    }
}

export async function POST(
    request: NextRequest,
    { params }: { params: Promise<{ path: string[] }> }
) {
    try {
        const { path } = await params
        const apiPath = path.join('/')
        const url = `${MOPPY_API_BASE}/api/${apiPath}`

        const body = await request.text()
        console.log(`Proxying POST request to: ${url}`, body ? `with body: ${body}` : 'without body')

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: body || undefined,
        })

        const data = await response.text()

        if (!response.ok) {
            return NextResponse.json(
                { error: `API Error: ${data}`, status: response.status },
                { status: response.status }
            )
        }

        try {
            const jsonData = JSON.parse(data)
            return NextResponse.json(jsonData)
        } catch {
            return new NextResponse(data, {
                status: response.status,
                headers: { 'Content-Type': response.headers.get('Content-Type') || 'text/plain' }
            })
        }
    } catch (error) {
        console.error('Proxy error:', error)
        return NextResponse.json(
            { error: 'Failed to connect to Moppy API server', details: error instanceof Error ? error.message : 'Unknown error' },
            { status: 503 }
        )
    }
}

export async function PUT(
    request: NextRequest,
    { params }: { params: Promise<{ path: string[] }> }
) {
    try {
        const { path } = await params
        const apiPath = path.join('/')
        const url = `${MOPPY_API_BASE}/api/${apiPath}`

        const body = await request.text()
        console.log(`Proxying PUT request to: ${url}`, body ? `with body: ${body}` : 'without body')

        const response = await fetch(url, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: body || undefined,
        })

        const data = await response.text()

        if (!response.ok) {
            return NextResponse.json(
                { error: `API Error: ${data}`, status: response.status },
                { status: response.status }
            )
        }

        try {
            const jsonData = JSON.parse(data)
            return NextResponse.json(jsonData)
        } catch {
            return new NextResponse(data, {
                status: response.status,
                headers: { 'Content-Type': response.headers.get('Content-Type') || 'text/plain' }
            })
        }
    } catch (error) {
        console.error('Proxy error:', error)
        return NextResponse.json(
            { error: 'Failed to connect to Moppy API server', details: error instanceof Error ? error.message : 'Unknown error' },
            { status: 503 }
        )
    }
}

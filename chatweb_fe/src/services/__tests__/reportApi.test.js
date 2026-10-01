import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'
import { reportApi } from '../reportApi.js'

describe('reportApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls getMyReports with default parameters', async () => {
    const mockResponse = {
      code: 200,
      data: {
        content: [],
        pageNo: 0,
        pageSize: 10,
        totalElements: 0,
        totalPages: 0,
        last: true,
      },
    }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await reportApi.getMyReports()

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/reports/me?page=0&size=10&sortDir=desc')
    expect(result).toEqual(mockResponse)
  })

  it('calls getMyReports with custom parameters', async () => {
    const mockResponse = { code: 200, data: { content: [] } }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await reportApi.getMyReports(2, 20, 'asc')

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/reports/me?page=2&size=20&sortDir=asc')
    expect(result).toEqual(mockResponse)
  })

  it('calls cancelReport with correct HTTP method and encoded id', async () => {
    const mockResponse = { code: 200, message: 'Report cancelled' }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await reportApi.cancelReport(105)

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/reports/105', { method: 'DELETE' })
    expect(result).toEqual(mockResponse)
  })

  it('encodes special characters in cancelReport id', async () => {
    const mockResponse = { code: 200, message: 'Report cancelled' }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    await reportApi.cancelReport('special/id#123')

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/reports/special%2Fid%23123', { method: 'DELETE' })
  })

  it('calls createReport with POST method and body', async () => {
    const body = { reportedUserId: 'target1', reason: 'SPAM', details: 'spamming' }
    const mockResponse = { code: 201, data: { id: 1 } }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await reportApi.createReport(body)

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/reports', { method: 'POST', body })
    expect(result).toEqual(mockResponse)
  })

  it('calls admin report endpoints with correct URLs and parameters', async () => {
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200, data: {} })

    // getAdminReports with filters
    await reportApi.getAdminReports({ status: 'PENDING', page: 0, size: 10, empty: '' })
    expect(apiRequestSpy).toHaveBeenCalledWith('/api/admin/reports?status=PENDING&page=0&size=10')

    // getAdminReportStatistics
    await reportApi.getAdminReportStatistics()
    expect(apiRequestSpy).toHaveBeenCalledWith('/api/admin/reports/statistics')

    // getAdminReport
    await reportApi.getAdminReport('rep#42')
    expect(apiRequestSpy).toHaveBeenCalledWith('/api/admin/reports/rep%2342')

    // resolveAdminReport
    const resolveBody = { status: 'RESOLVED', resolutionNote: 'Resolved by admin' }
    await reportApi.resolveAdminReport('rep#42', resolveBody)
    expect(apiRequestSpy).toHaveBeenCalledWith('/api/admin/reports/rep%2342/resolve', { method: 'PUT', body: resolveBody })

    // deleteAdminReport
    await reportApi.deleteAdminReport('rep#42')
    expect(apiRequestSpy).toHaveBeenCalledWith('/api/admin/reports/rep%2342', { method: 'DELETE' })
  })
})

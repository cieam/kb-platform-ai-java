import request from './request'

export const pageKb = (params) => request.get('/kb/page', { params })

export const getKb = (id) => request.get('/kb/' + id)

export const createKb = (data) => request.post('/kb', data)

export const updateKb = (data) => request.put('/kb', data)

export const deleteKb = (id) => request.delete('/kb/' + id)

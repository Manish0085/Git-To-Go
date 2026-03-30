import api from './axios';

export const adminAPI = {
  getDashboard: () => api.get('/admin/dashboard'),
  getUsers: (page = 0, size = 20) => api.get(`/admin/users?page=${page}&size=${size}`),
  changeRole: (userId, role) => api.put(`/admin/users/${userId}/role?role=${role}`),
  deleteUser: (userId) => api.delete(`/admin/users/${userId}`),
  getProjects: (page = 0, size = 20) => api.get(`/admin/projects?page=${page}&size=${size}`),
  forceStopDeployment: (id) => api.post(`/admin/deployments/${id}/stop`),
  getAuditLogs: (page = 0, size = 20) => api.get(`/audit/activity?page=${page}&size=${size}`),
};

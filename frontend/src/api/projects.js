import api from './axios';

export const projectAPI = {
  getAll: (page = 0, size = 10) => api.get(`/projects?page=${page}&size=${size}`),
  getById: (id) => api.get(`/projects/${id}`),
  create: (data) => api.post('/projects', data),
  update: (id, data) => api.put(`/projects/${id}`, data),
  delete: (id) => api.delete(`/projects/${id}`),

  // Deployment
  deploy: (projectId) => api.post(`/projects/${projectId}/deploy`),
  getDeployments: (projectId) => api.get(`/projects/${projectId}/deployments`),

  // Webhook
  getWebhookConfig: (projectId) => api.get(`/projects/${projectId}/webhook`),
  enableWebhook: (projectId) => api.post(`/projects/${projectId}/webhook/enable`),
  disableWebhook: (projectId) => api.post(`/projects/${projectId}/webhook/disable`),
  regenerateSecret: (projectId) => api.post(`/projects/${projectId}/webhook/regenerate`),
};

import api from './axios';

export const deploymentAPI = {
  getById: (id) => api.get(`/deployments/${id}`),
  stop: (id) => api.post(`/deployments/${id}/stop`),
  restart: (id) => api.post(`/deployments/${id}/restart`),

  // Logs
  getBuildLogs: (id) => api.get(`/deployments/${id}/logs/build`),
  getBuildLogsTail: (id, lines = 50) => api.get(`/deployments/${id}/logs/build/tail?lines=${lines}`),
  getRuntimeLogs: (id, lines = 100) => api.get(`/deployments/${id}/logs/runtime?lines=${lines}`),
  startLogStream: (id) => api.post(`/deployments/${id}/logs/stream/start`),
  stopLogStream: (id) => api.post(`/deployments/${id}/logs/stream/stop`),

  // Monitoring
  getStats: (id) => api.get(`/monitoring/deployments/${id}/stats`),
  getHealth: (id) => api.get(`/monitoring/deployments/${id}/health`),
};

export const monitoringAPI = {
  getAllStats: () => api.get('/monitoring/stats'),
};

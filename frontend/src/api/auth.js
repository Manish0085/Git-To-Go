import api from './axios';

export const authAPI = {
  signup: (data) => api.post('/auth/signup', data),
  login: (data) => api.post('/auth/login', data),
  logout: () => api.post('/auth/logout'),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  verifyEmail: (token) => api.get(`/auth/verify-email?token=${token}`),
  resendVerification: (email) => api.post(`/auth/resend-verification?email=${email}`),
  getProfile: () => api.get('/user/me'),
  getGitHubRepos: () => api.get('/user/github/repos'),
};

import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { projectAPI } from '../../api/projects';
import { deploymentAPI } from '../../api/deployments';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Rocket, Play, Square, RotateCw, Trash2, ExternalLink,
  Terminal, Activity, Settings, Globe, GitBranch, Clock, Webhook,
  Plus, X, Eye, EyeOff, Save
} from 'lucide-react';
import StatusBadge from '../../components/ui/StatusBadge';
import Button from '../../components/ui/Button';
import toast from 'react-hot-toast';

export default function ProjectDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [deployments, setDeployments] = useState([]);
  const [logs, setLogs] = useState([]);
  const [stats, setStats] = useState(null);
  const [webhook, setWebhook] = useState(null);
  const [activeTab, setActiveTab] = useState('overview');
  const [loading, setLoading] = useState(true);
  const [deploying, setDeploying] = useState(false);
  const [envVars, setEnvVars] = useState([]);
  const [envEditing, setEnvEditing] = useState(false);
  const [envSaving, setEnvSaving] = useState(false);
  const [visibleVars, setVisibleVars] = useState({});

  const fetchData = () => {
    Promise.all([
      projectAPI.getById(id),
      projectAPI.getDeployments(id),
      projectAPI.getWebhookConfig(id).catch(() => null),
    ]).then(([projRes, deplRes, webhookRes]) => {
      setProject(projRes.data);
      setDeployments(deplRes.data || []);
      if (webhookRes) setWebhook(webhookRes.data);
      const vars = projRes.data?.envVariables || {};
      setEnvVars(Object.entries(vars).map(([key, value]) => ({ key, value })));

      const latestDeploy = (deplRes.data || [])[0];
      if (latestDeploy) {
        deploymentAPI.getBuildLogs(latestDeploy.id).then((r) => setLogs(r.data || [])).catch(() => {});
        deploymentAPI.getStats(latestDeploy.id).then((r) => setStats(r.data)).catch(() => {});
      }
    }).finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, [id]);

  const handleDeploy = async () => {
    setDeploying(true);
    try {
      await projectAPI.deploy(id);
      toast.success('Deployment started!');
      setTimeout(fetchData, 2000);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Deploy failed');
    } finally {
      setDeploying(false);
    }
  };

  const handleStop = async (deployId) => {
    try {
      await deploymentAPI.stop(deployId);
      toast.success('Stopped');
      fetchData();
    } catch (err) {
      toast.error('Failed to stop');
    }
  };

  const handleRestart = async (deployId) => {
    try {
      await deploymentAPI.restart(deployId);
      toast.success('Restarted');
      fetchData();
    } catch (err) {
      toast.error('Failed to restart');
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('Are you sure? This will delete the project and all deployments.')) return;
    try {
      await projectAPI.delete(id);
      toast.success('Project deleted');
      navigate('/dashboard');
    } catch (err) {
      toast.error('Failed to delete');
    }
  };

  const toggleWebhook = async () => {
    try {
      const res = webhook?.autoDeployEnabled
        ? await projectAPI.disableWebhook(id)
        : await projectAPI.enableWebhook(id);
      setWebhook(res.data);
      toast.success(res.data.autoDeployEnabled ? 'Auto-deploy enabled' : 'Auto-deploy disabled');
    } catch (err) {
      toast.error('Failed to toggle auto-deploy');
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
      </div>
    );
  }

  if (!project) return <p className="text-gray-400">Project not found</p>;

  const latestDeploy = deployments[0];
  const tabs = [
    { id: 'overview', label: 'Overview', icon: Activity },
    { id: 'logs', label: 'Build Logs', icon: Terminal },
    { id: 'deployments', label: 'Deployments', icon: Rocket },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-white">{project.name}</h1>
            <StatusBadge status={project.status} />
          </div>
          <p className="text-sm text-gray-500 mt-1">{project.repoUrl}</p>
        </div>
        <div className="flex items-center gap-2">
          {project.deployedUrl && (
            <a href={project.deployedUrl} target="_blank" rel="noopener noreferrer">
              <Button variant="ghost">
                <ExternalLink className="w-4 h-4" />
                Visit
              </Button>
            </a>
          )}
          <Button onClick={handleDeploy} loading={deploying} variant="success">
            <Rocket className="w-4 h-4" />
            Deploy
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-gray-900 p-1 rounded-lg w-fit">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-all ${
              activeTab === tab.id
                ? 'bg-gray-800 text-white'
                : 'text-gray-500 hover:text-gray-300'
            }`}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <AnimatePresence mode="wait">
        <motion.div key={activeTab} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
          {activeTab === 'overview' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Project Info */}
              <div className="lg:col-span-2 space-y-4">
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                  <h3 className="text-sm font-medium text-gray-300 mb-4">Project Info</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div><span className="text-gray-500">Branch:</span> <span className="text-white ml-2">{project.branch}</span></div>
                    <div><span className="text-gray-500">Port:</span> <span className="text-white ml-2">{project.port}</span></div>
                    <div><span className="text-gray-500">Auto Deploy:</span> <span className={`ml-2 ${webhook?.autoDeployEnabled ? 'text-emerald-400' : 'text-gray-600'}`}>{webhook?.autoDeployEnabled ? 'Enabled' : 'Disabled'}</span></div>
                    <div><span className="text-gray-500">Deployments:</span> <span className="text-white ml-2">{deployments.length}</span></div>
                  </div>
                </div>

                {/* Stats */}
                {stats && stats.containerStatus === 'RUNNING' && (
                  <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                    <h3 className="text-sm font-medium text-gray-300 mb-4">Container Stats</h3>
                    <div className="grid grid-cols-3 gap-4">
                      <div>
                        <p className="text-xs text-gray-500 mb-1">CPU</p>
                        <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                          <div className="h-full bg-indigo-500 rounded-full transition-all" style={{ width: `${Math.min(stats.cpuUsagePercent, 100)}%` }} />
                        </div>
                        <p className="text-xs text-gray-400 mt-1">{stats.cpuUsagePercent}%</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500 mb-1">Memory</p>
                        <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                          <div className="h-full bg-emerald-500 rounded-full transition-all" style={{ width: `${Math.min(stats.memoryUsagePercent, 100)}%` }} />
                        </div>
                        <p className="text-xs text-gray-400 mt-1">{stats.memoryUsageMb}MB / {stats.memoryLimitMb}MB</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500 mb-1">Uptime</p>
                        <p className="text-lg font-bold text-white">{stats.uptime || '0m'}</p>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Quick Actions */}
              <div className="space-y-4">
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 space-y-3">
                  <h3 className="text-sm font-medium text-gray-300 mb-2">Quick Actions</h3>
                  {latestDeploy && (
                    <>
                      <Button variant="ghost" className="w-full" onClick={() => handleStop(latestDeploy.id)}>
                        <Square className="w-4 h-4" /> Stop
                      </Button>
                      <Button variant="ghost" className="w-full" onClick={() => handleRestart(latestDeploy.id)}>
                        <RotateCw className="w-4 h-4" /> Restart
                      </Button>
                    </>
                  )}
                  <Button variant="ghost" className="w-full" onClick={toggleWebhook}>
                    <Webhook className="w-4 h-4" /> {webhook?.autoDeployEnabled ? 'Disable' : 'Enable'} Auto Deploy
                  </Button>
                  <Button variant="danger" className="w-full" onClick={handleDelete}>
                    <Trash2 className="w-4 h-4" /> Delete Project
                  </Button>
                </div>

                {/* Webhook Info */}
                {webhook?.autoDeployEnabled && webhook?.webhookUrl && (
                  <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                    <h3 className="text-sm font-medium text-gray-300 mb-3">Webhook Config</h3>
                    <div className="space-y-2 text-xs">
                      <div>
                        <p className="text-gray-500 mb-1">Payload URL:</p>
                        <p className="text-indigo-400 break-all bg-gray-800 p-2 rounded">{webhook.webhookUrl}</p>
                      </div>
                      <div>
                        <p className="text-gray-500 mb-1">Secret:</p>
                        <p className="text-amber-400 break-all bg-gray-800 p-2 rounded font-mono">{webhook.webhookSecret}</p>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'logs' && (
            <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
              <div className="flex items-center justify-between px-5 py-3 border-b border-gray-800">
                <h3 className="text-sm font-medium text-gray-300">Build Logs</h3>
                <span className="text-xs text-gray-600">{logs.length} lines</span>
              </div>
              <div className="p-4 font-mono text-xs max-h-[600px] overflow-y-auto space-y-0.5">
                {logs.length === 0 ? (
                  <p className="text-gray-600 py-8 text-center">No build logs yet. Deploy to see logs.</p>
                ) : (
                  logs.map((log, i) => (
                    <div key={i} className={`flex gap-3 py-0.5 ${log.level === 'ERROR' ? 'text-red-400' : 'text-gray-400'}`}>
                      <span className="text-gray-600 shrink-0 w-44">{log.timestamp?.replace('T', ' ').slice(0, 19)}</span>
                      <span className={`shrink-0 w-12 ${log.level === 'ERROR' ? 'text-red-400' : log.level === 'WARN' ? 'text-amber-400' : 'text-blue-400'}`}>
                        {log.level}
                      </span>
                      <span>{log.message}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {activeTab === 'deployments' && (
            <div className="space-y-3">
              {deployments.length === 0 ? (
                <p className="text-gray-600 text-center py-12">No deployments yet</p>
              ) : (
                deployments.map((dep, i) => (
                  <motion.div
                    key={dep.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.05 }}
                    className="flex items-center justify-between p-4 bg-gray-900 border border-gray-800 rounded-xl"
                  >
                    <div className="flex items-center gap-4">
                      <div className="text-center">
                        <p className="text-xs text-gray-500">Version</p>
                        <p className="text-lg font-bold text-white">v{dep.version}</p>
                      </div>
                      <StatusBadge status={dep.status} />
                      {dep.deployedUrl && (
                        <a href={dep.deployedUrl} target="_blank" rel="noopener noreferrer" className="text-xs text-indigo-400 hover:underline">
                          {dep.deployedUrl}
                        </a>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-gray-600">{dep.createdAt?.replace('T', ' ').slice(0, 19)}</span>
                      {dep.status === 'RUNNING' && (
                        <>
                          <Button variant="ghost" onClick={() => handleStop(dep.id)}>
                            <Square className="w-3 h-3" />
                          </Button>
                          <Button variant="ghost" onClick={() => handleRestart(dep.id)}>
                            <RotateCw className="w-3 h-3" />
                          </Button>
                        </>
                      )}
                    </div>
                  </motion.div>
                ))
              )}
            </div>
          )}

          {activeTab === 'settings' && (
            <div className="max-w-xl space-y-6">
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-medium text-gray-300">Environment Variables</h3>
                  {!envEditing ? (
                    <button
                      onClick={() => setEnvEditing(true)}
                      className="text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
                    >
                      Edit
                    </button>
                  ) : (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => {
                          const vars = project.envVariables || {};
                          setEnvVars(Object.entries(vars).map(([key, value]) => ({ key, value })));
                          setEnvEditing(false);
                        }}
                        className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
                      >
                        Cancel
                      </button>
                      <Button
                        variant="primary"
                        loading={envSaving}
                        onClick={async () => {
                          setEnvSaving(true);
                          try {
                            const envVariables = {};
                            envVars.forEach(({ key, value }) => { if (key.trim()) envVariables[key.trim()] = value; });
                            await projectAPI.update(id, { envVariables });
                            toast.success('Environment variables saved');
                            setEnvEditing(false);
                            fetchData();
                          } catch (err) {
                            toast.error(err.response?.data?.message || 'Failed to save');
                          } finally {
                            setEnvSaving(false);
                          }
                        }}
                      >
                        <Save className="w-3 h-3" />
                        Save
                      </Button>
                    </div>
                  )}
                </div>

                {envEditing ? (
                  <div className="space-y-2">
                    {envVars.map((env, i) => (
                      <div key={i} className="flex items-center gap-2">
                        <input
                          className="flex-1 px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 font-mono"
                          placeholder="KEY"
                          value={env.key}
                          onChange={(e) => {
                            const updated = [...envVars];
                            updated[i].key = e.target.value;
                            setEnvVars(updated);
                          }}
                        />
                        <input
                          className="flex-1 px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 font-mono"
                          placeholder="value"
                          type={visibleVars[i] ? 'text' : 'password'}
                          value={env.value}
                          onChange={(e) => {
                            const updated = [...envVars];
                            updated[i].value = e.target.value;
                            setEnvVars(updated);
                          }}
                        />
                        <button
                          type="button"
                          onClick={() => setVisibleVars({ ...visibleVars, [i]: !visibleVars[i] })}
                          className="p-2 text-gray-500 hover:text-gray-300 transition-colors"
                        >
                          {visibleVars[i] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                        <button
                          type="button"
                          onClick={() => setEnvVars(envVars.filter((_, idx) => idx !== i))}
                          className="p-2 text-gray-500 hover:text-red-400 transition-colors"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                    <button
                      type="button"
                      onClick={() => setEnvVars([...envVars, { key: '', value: '' }])}
                      className="flex items-center gap-1 mt-2 text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
                    >
                      <Plus className="w-3 h-3" /> Add Variable
                    </button>
                  </div>
                ) : envVars.length > 0 ? (
                  <div className="space-y-2">
                    {envVars.map((env, i) => (
                      <div key={i} className="flex items-center gap-2 text-xs bg-gray-800 p-2 rounded font-mono">
                        <span className="text-indigo-400">{env.key}</span>
                        <span className="text-gray-600">=</span>
                        <span className="text-gray-400">{'*'.repeat(8)}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-gray-600 text-sm">No environment variables set</p>
                )}
              </div>

              <div className="bg-red-500/5 border border-red-500/20 rounded-xl p-5">
                <h3 className="text-sm font-medium text-red-400 mb-2">Danger Zone</h3>
                <p className="text-xs text-gray-500 mb-4">This action is irreversible. All deployments will be stopped and deleted.</p>
                <Button variant="danger" onClick={handleDelete}>
                  <Trash2 className="w-4 h-4" />
                  Delete Project
                </Button>
              </div>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}

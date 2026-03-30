import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { projectAPI } from '../../api/projects';
import { authAPI } from '../../api/auth';
import { motion } from 'framer-motion';
import { FolderGit2, GitBranch, Globe, Plus, Search, X } from 'lucide-react';
import Input from '../../components/ui/Input';
import Button from '../../components/ui/Button';
import toast from 'react-hot-toast';

export default function NewProjectPage() {
  const [form, setForm] = useState({ name: '', repoUrl: '', branch: 'main', port: 3000 });
  const [envVars, setEnvVars] = useState([]);
  const [repos, setRepos] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingRepos, setLoadingRepos] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    authAPI.getGitHubRepos()
      .then((res) => setRepos(res.data || []))
      .catch(() => {})
      .finally(() => setLoadingRepos(false));
  }, []);

  const selectRepo = (repo) => {
    setForm({
      ...form,
      name: repo.name,
      repoUrl: repo.cloneUrl,
      branch: repo.defaultBranch || 'main',
    });
  };

  const filteredRepos = repos.filter((r) =>
    r.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const envVariables = {};
      envVars.forEach(({ key, value }) => { if (key.trim()) envVariables[key.trim()] = value; });
      const res = await projectAPI.create({ ...form, envVariables });
      toast.success('Project created!');
      navigate(`/projects/${res.data.id}`);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create project');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold text-white mb-2">New Project</h1>
      <p className="text-gray-400 text-sm mb-8">Select a GitHub repo or enter details manually</p>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Left — GitHub Repos */}
        <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }}>
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <h3 className="text-sm font-medium text-gray-300 mb-4">Your GitHub Repositories</h3>

            <div className="relative mb-4">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
              <input
                className="w-full pl-10 pr-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                placeholder="Search repos..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>

            <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
              {loadingRepos ? (
                <div className="flex justify-center py-8">
                  <div className="w-6 h-6 border-2 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
                </div>
              ) : filteredRepos.length === 0 ? (
                <p className="text-center text-gray-600 py-8 text-sm">
                  {repos.length === 0 ? 'Login with GitHub to see your repos' : 'No repos found'}
                </p>
              ) : (
                filteredRepos.map((repo) => (
                  <button
                    key={repo.id}
                    onClick={() => selectRepo(repo)}
                    className={`w-full text-left p-3 rounded-lg border transition-all text-sm ${
                      form.repoUrl === repo.cloneUrl
                        ? 'border-indigo-500 bg-indigo-500/10'
                        : 'border-gray-800 hover:border-gray-700 hover:bg-gray-800'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <FolderGit2 className="w-4 h-4 text-gray-400 shrink-0" />
                      <span className="text-white font-medium truncate">{repo.name}</span>
                      {repo.isPrivate && (
                        <span className="text-[10px] px-1.5 py-0.5 bg-amber-500/20 text-amber-400 rounded">Private</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 mt-1 truncate">{repo.description || 'No description'}</p>
                    <div className="flex items-center gap-3 mt-2 text-xs text-gray-600">
                      {repo.language && <span>{repo.language}</span>}
                      <span>{repo.stargazersCount} stars</span>
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>
        </motion.div>

        {/* Right — Form */}
        <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}>
          <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-800 rounded-xl p-5 space-y-5">
            <h3 className="text-sm font-medium text-gray-300 mb-2">Project Details</h3>

            <Input
              label="Project Name"
              icon={FolderGit2}
              placeholder="my-awesome-app"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            <Input
              label="Repository URL"
              icon={Globe}
              placeholder="https://github.com/user/repo.git"
              value={form.repoUrl}
              onChange={(e) => setForm({ ...form, repoUrl: e.target.value })}
              required
            />
            <div className="grid grid-cols-2 gap-4">
              <Input
                label="Branch"
                icon={GitBranch}
                value={form.branch}
                onChange={(e) => setForm({ ...form, branch: e.target.value })}
                required
              />
              <Input
                label="Port"
                type="number"
                value={form.port}
                onChange={(e) => setForm({ ...form, port: parseInt(e.target.value) || 3000 })}
                required
                min={1}
                max={65535}
              />
            </div>

            {/* Environment Variables */}
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">Environment Variables</label>
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
                      type="password"
                      value={env.value}
                      onChange={(e) => {
                        const updated = [...envVars];
                        updated[i].value = e.target.value;
                        setEnvVars(updated);
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => setEnvVars(envVars.filter((_, idx) => idx !== i))}
                      className="p-2 text-gray-500 hover:text-red-400 transition-colors"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setEnvVars([...envVars, { key: '', value: '' }])}
                className="mt-2 text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
              >
                + Add Variable
              </button>
            </div>

            <Button type="submit" loading={loading} className="w-full">
              <Plus className="w-4 h-4" />
              Create Project
            </Button>
          </form>
        </motion.div>
      </div>
    </div>
  );
}

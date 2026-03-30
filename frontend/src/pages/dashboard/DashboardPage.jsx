import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { projectAPI } from '../../api/projects';
import { monitoringAPI } from '../../api/deployments';
import { motion } from 'framer-motion';
import { Plus, FolderGit2, Rocket, Activity, AlertTriangle } from 'lucide-react';
import StatusBadge from '../../components/ui/StatusBadge';
import StatsCard from '../../components/ui/StatsCard';
import Button from '../../components/ui/Button';

export default function DashboardPage() {
  const [projects, setProjects] = useState([]);
  const [stats, setStats] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      projectAPI.getAll(0, 50).catch(() => ({ data: { content: [] } })),
      monitoringAPI.getAllStats().catch(() => ({ data: [] })),
    ]).then(([projRes, statsRes]) => {
      setProjects(projRes.data.content || []);
      setStats(statsRes.data || []);
    }).finally(() => setLoading(false));
  }, []);

  const running = projects.filter((p) => p.status === 'RUNNING').length;
  const failed = projects.filter((p) => p.status === 'FAILED').length;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Dashboard</h1>
          <p className="text-gray-400 text-sm mt-1">Overview of your deployments</p>
        </div>
        <Link to="/projects/new">
          <Button>
            <Plus className="w-4 h-4" />
            New Project
          </Button>
        </Link>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatsCard icon={FolderGit2} label="Total Projects" value={projects.length} color="indigo" />
        <StatsCard icon={Rocket} label="Running" value={running} color="emerald" />
        <StatsCard icon={AlertTriangle} label="Failed" value={failed} color="red" />
        <StatsCard icon={Activity} label="Monitored" value={stats.length} color="purple" />
      </div>

      {/* Projects Grid */}
      {projects.length === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-center py-20 border border-dashed border-gray-800 rounded-xl"
        >
          <FolderGit2 className="w-12 h-12 text-gray-700 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-gray-400 mb-2">No projects yet</h3>
          <p className="text-gray-600 mb-6">Create your first project and deploy it in seconds</p>
          <Link to="/projects/new">
            <Button>
              <Plus className="w-4 h-4" />
              Create Project
            </Button>
          </Link>
        </motion.div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.map((project, i) => (
            <motion.div
              key={project.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05 }}
            >
              <Link
                to={`/projects/${project.id}`}
                className="block p-5 bg-gray-900 border border-gray-800 rounded-xl hover:border-indigo-500/30 hover:bg-gray-900/80 transition-all duration-300 group"
              >
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-semibold text-white group-hover:text-indigo-400 transition-colors truncate">
                    {project.name}
                  </h3>
                  <StatusBadge status={project.status} />
                </div>
                <p className="text-xs text-gray-500 truncate mb-3">{project.repoUrl}</p>
                <div className="flex items-center justify-between text-xs text-gray-600">
                  <span>Branch: {project.branch}</span>
                  <span>Port: {project.port}</span>
                </div>
                {project.deployedUrl && (
                  <div className="mt-3 pt-3 border-t border-gray-800">
                    <p className="text-xs text-emerald-400 truncate">{project.deployedUrl}</p>
                  </div>
                )}
              </Link>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

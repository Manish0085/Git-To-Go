import { useState, useEffect } from 'react';
import { monitoringAPI } from '../../api/deployments';
import { motion } from 'framer-motion';
import { Activity, Cpu, HardDrive, Clock } from 'lucide-react';
import StatusBadge from '../../components/ui/StatusBadge';

export default function MonitoringPage() {
  const [stats, setStats] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchStats = () => {
    monitoringAPI.getAllStats()
      .then((res) => setStats(res.data || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 15000); // Refresh every 15s
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-8">
        <Activity className="w-6 h-6 text-indigo-400" />
        <h1 className="text-2xl font-bold text-white">Monitoring</h1>
        <span className="text-xs text-gray-600 bg-gray-800 px-2 py-1 rounded">Auto-refresh 15s</span>
      </div>

      {stats.length === 0 ? (
        <div className="text-center py-20 border border-dashed border-gray-800 rounded-xl">
          <Activity className="w-12 h-12 text-gray-700 mx-auto mb-4" />
          <p className="text-gray-400">No running containers to monitor</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {stats.map((stat, i) => (
            <motion.div
              key={stat.deploymentId}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.1 }}
              className="bg-gray-900 border border-gray-800 rounded-xl p-5"
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-white">{stat.projectName}</h3>
                <StatusBadge status={stat.containerStatus} />
              </div>

              <div className="space-y-4">
                {/* CPU */}
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-gray-500 flex items-center gap-1"><Cpu className="w-3 h-3" /> CPU</span>
                    <span className="text-white">{stat.cpuUsagePercent}%</span>
                  </div>
                  <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                    <motion.div
                      className={`h-full rounded-full ${stat.cpuUsagePercent > 80 ? 'bg-red-500' : stat.cpuUsagePercent > 50 ? 'bg-amber-500' : 'bg-indigo-500'}`}
                      initial={{ width: 0 }}
                      animate={{ width: `${Math.min(stat.cpuUsagePercent, 100)}%` }}
                      transition={{ duration: 1 }}
                    />
                  </div>
                </div>

                {/* Memory */}
                <div>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-gray-500 flex items-center gap-1"><HardDrive className="w-3 h-3" /> Memory</span>
                    <span className="text-white">{stat.memoryUsageMb}MB / {stat.memoryLimitMb}MB</span>
                  </div>
                  <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                    <motion.div
                      className={`h-full rounded-full ${stat.memoryUsagePercent > 80 ? 'bg-red-500' : stat.memoryUsagePercent > 50 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                      initial={{ width: 0 }}
                      animate={{ width: `${Math.min(stat.memoryUsagePercent, 100)}%` }}
                      transition={{ duration: 1 }}
                    />
                  </div>
                </div>

                {/* Uptime */}
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <Clock className="w-3 h-3" />
                  Uptime: <span className="text-white">{stat.uptime || '0m'}</span>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

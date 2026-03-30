import { useState, useEffect } from 'react';
import { adminAPI } from '../../api/admin';
import { motion } from 'framer-motion';
import { Clock, CheckCircle, XCircle } from 'lucide-react';

export default function ActivityPage() {
  const [activity, setActivity] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminAPI.getAuditLogs(0, 50)
      .then((res) => setActivity(res.data.content || []))
      .catch(() => {})
      .finally(() => setLoading(false));
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
        <Clock className="w-6 h-6 text-indigo-400" />
        <h1 className="text-2xl font-bold text-white">Activity Log</h1>
      </div>

      {activity.length === 0 ? (
        <p className="text-gray-600 text-center py-12">No activity yet</p>
      ) : (
        <div className="space-y-2">
          {activity.map((log, i) => (
            <motion.div
              key={log.id}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: i * 0.03 }}
              className="flex items-center gap-4 p-4 bg-gray-900 border border-gray-800 rounded-xl"
            >
              {log.result === 'SUCCESS' ? (
                <CheckCircle className="w-5 h-5 text-emerald-400 shrink-0" />
              ) : (
                <XCircle className="w-5 h-5 text-red-400 shrink-0" />
              )}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-white">{log.action.replace(/_/g, ' ')}</span>
                  {log.resourceName && (
                    <span className="text-xs bg-gray-800 text-gray-400 px-2 py-0.5 rounded">{log.resourceName}</span>
                  )}
                </div>
                {log.details && <p className="text-xs text-gray-500 mt-1 truncate">{log.details}</p>}
              </div>
              <div className="text-right shrink-0">
                <p className="text-xs text-gray-600">{log.timestamp?.replace('T', ' ').slice(0, 19)}</p>
                <p className="text-[10px] text-gray-700">{log.ipAddress}</p>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

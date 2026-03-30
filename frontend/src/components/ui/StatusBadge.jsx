import { motion } from 'framer-motion';

const statusConfig = {
  RUNNING: { color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30', dot: 'bg-emerald-400', pulse: true },
  CREATED: { color: 'bg-blue-500/20 text-blue-400 border-blue-500/30', dot: 'bg-blue-400' },
  BUILDING: { color: 'bg-amber-500/20 text-amber-400 border-amber-500/30', dot: 'bg-amber-400', pulse: true },
  DEPLOYING: { color: 'bg-purple-500/20 text-purple-400 border-purple-500/30', dot: 'bg-purple-400', pulse: true },
  QUEUED: { color: 'bg-gray-500/20 text-gray-400 border-gray-500/30', dot: 'bg-gray-400' },
  CLONING: { color: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30', dot: 'bg-cyan-400', pulse: true },
  STOPPED: { color: 'bg-gray-500/20 text-gray-400 border-gray-500/30', dot: 'bg-gray-400' },
  FAILED: { color: 'bg-red-500/20 text-red-400 border-red-500/30', dot: 'bg-red-400' },
};

export default function StatusBadge({ status }) {
  const config = statusConfig[status] || statusConfig.CREATED;

  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${config.color}`}>
      <span className="relative flex h-2 w-2">
        {config.pulse && (
          <motion.span
            className={`absolute inline-flex h-full w-full rounded-full ${config.dot} opacity-75`}
            animate={{ scale: [1, 1.5, 1], opacity: [0.75, 0, 0.75] }}
            transition={{ duration: 1.5, repeat: Infinity }}
          />
        )}
        <span className={`relative inline-flex rounded-full h-2 w-2 ${config.dot}`} />
      </span>
      {status}
    </span>
  );
}

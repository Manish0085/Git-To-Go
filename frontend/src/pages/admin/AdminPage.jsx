import { useState, useEffect } from 'react';
import { adminAPI } from '../../api/admin';
import { motion } from 'framer-motion';
import { Shield, Users, FolderGit2, Rocket, AlertTriangle, Trash2 } from 'lucide-react';
import StatsCard from '../../components/ui/StatsCard';
import Button from '../../components/ui/Button';
import toast from 'react-hot-toast';

export default function AdminPage() {
  const [dashboard, setDashboard] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchData = () => {
    Promise.all([
      adminAPI.getDashboard(),
      adminAPI.getUsers(0, 50),
    ]).then(([dashRes, usersRes]) => {
      setDashboard(dashRes.data);
      setUsers(usersRes.data.content || []);
    }).catch(() => toast.error('Admin access required'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, []);

  const handleDeleteUser = async (userId, email) => {
    if (!window.confirm(`Delete user ${email} and ALL their data?`)) return;
    try {
      await adminAPI.deleteUser(userId);
      toast.success('User deleted');
      fetchData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed');
    }
  };

  const handleChangeRole = async (userId, currentRole) => {
    const newRole = currentRole === 'ADMIN' ? 'USER' : 'ADMIN';
    try {
      await adminAPI.changeRole(userId, newRole);
      toast.success(`Role changed to ${newRole}`);
      fetchData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed');
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-red-500/30 border-t-red-500 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-8">
        <Shield className="w-6 h-6 text-red-400" />
        <h1 className="text-2xl font-bold text-white">Admin Panel</h1>
      </div>

      {/* Stats */}
      {dashboard && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
          <StatsCard icon={Users} label="Total Users" value={dashboard.totalUsers} color="indigo" />
          <StatsCard icon={FolderGit2} label="Total Projects" value={dashboard.totalProjects} color="purple" />
          <StatsCard icon={Rocket} label="Total Deploys" value={dashboard.totalDeployments} color="emerald" />
          <StatsCard icon={Rocket} label="Running" value={dashboard.runningDeployments} color="emerald" />
          <StatsCard icon={AlertTriangle} label="Failed" value={dashboard.failedDeployments} color="red" />
        </div>
      )}

      {/* Users Table */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-800">
          <h3 className="font-medium text-white">All Users</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800">
                <th className="text-left px-5 py-3 text-gray-500 font-medium">User</th>
                <th className="text-left px-5 py-3 text-gray-500 font-medium">Provider</th>
                <th className="text-left px-5 py-3 text-gray-500 font-medium">Role</th>
                <th className="text-left px-5 py-3 text-gray-500 font-medium">Verified</th>
                <th className="text-left px-5 py-3 text-gray-500 font-medium">Projects</th>
                <th className="text-left px-5 py-3 text-gray-500 font-medium">Joined</th>
                <th className="text-right px-5 py-3 text-gray-500 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user, i) => (
                <motion.tr
                  key={user.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: i * 0.03 }}
                  className="border-b border-gray-800/50 hover:bg-gray-800/30"
                >
                  <td className="px-5 py-3">
                    <div>
                      <p className="text-white font-medium">{user.name}</p>
                      <p className="text-xs text-gray-500">{user.email}</p>
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <span className="text-xs bg-gray-800 text-gray-400 px-2 py-1 rounded">{user.authProvider}</span>
                  </td>
                  <td className="px-5 py-3">
                    <button
                      onClick={() => handleChangeRole(user.id, user.role)}
                      className={`text-xs px-2 py-1 rounded cursor-pointer ${
                        user.role === 'ADMIN'
                          ? 'bg-red-500/20 text-red-400'
                          : 'bg-gray-800 text-gray-400 hover:bg-indigo-500/20 hover:text-indigo-400'
                      }`}
                    >
                      {user.role}
                    </button>
                  </td>
                  <td className="px-5 py-3">
                    {user.emailVerified ? (
                      <span className="text-emerald-400 text-xs">Verified</span>
                    ) : (
                      <span className="text-amber-400 text-xs">Pending</span>
                    )}
                  </td>
                  <td className="px-5 py-3 text-gray-400">{user.projectCount}</td>
                  <td className="px-5 py-3 text-xs text-gray-500">{user.createdAt?.slice(0, 10)}</td>
                  <td className="px-5 py-3 text-right">
                    <Button variant="danger" onClick={() => handleDeleteUser(user.id, user.email)} className="!py-1 !px-2 text-xs">
                      <Trash2 className="w-3 h-3" />
                    </Button>
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import { api, User } from '../../services/api';
import { Users, Plus, Trash2, CheckCircle2, Shield, User as UserIcon } from 'lucide-react';

export const AdminUsersPage: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'FACULTY' | 'ADMIN'>('FACULTY');
  const [dept, setDept] = useState('CSE');
  const [msg, setMsg] = useState<string | null>(null);

  const loadUsers = async () => {
    try {
      const data = await api.admin.getUsers();
      setUsers(data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.admin.createUser({
        name,
        email,
        password,
        role,
        department: dept,
      });
      setMsg('User created successfully.');
      setName('');
      setEmail('');
      setPassword('');
      loadUsers();
    } catch (err: any) {
      alert(`User creation failed: ${err.message}`);
    }
  };

  const handleDeleteUser = async (id: number) => {
    if (!window.confirm('Delete this user account?')) return;
    try {
      await api.admin.deleteUser(id);
      loadUsers();
    } catch (err: any) {
      alert(err.message);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">Faculty & Administrator Accounts</h1>
        <p className="mt-1 text-sm text-slate-600">
          Manage teacher accounts and administrative privileges.
        </p>
      </div>

      {msg && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-xl flex items-center space-x-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          <span>{msg}</span>
        </div>
      )}

      {/* Add User Form */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
          <Users className="w-5 h-5 text-blue-600" />
          <span>Add New Account</span>
        </h2>

        <form onSubmit={handleCreateUser} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          <input
            type="text"
            required
            placeholder="Full Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold"
          />
          <input
            type="email"
            required
            placeholder="Email Address"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-xs font-medium"
          />
          <input
            type="password"
            required
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-xs font-medium"
          />
          <select
            value={role}
            onChange={(e: any) => setRole(e.target.value)}
            className="bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-xs font-bold"
          >
            <option value="FACULTY">Faculty Role</option>
            <option value="ADMIN">Administrator Role</option>
          </select>
          <button
            type="submit"
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold flex items-center justify-center space-x-1 shadow-xs cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Create Account</span>
          </button>
        </form>
      </div>

      {/* Users List */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 mb-4">Registered Personnel</h2>
        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-200 text-slate-400 uppercase font-bold">
                <th className="py-2.5 px-3">Name</th>
                <th className="py-2.5 px-3">Email</th>
                <th className="py-2.5 px-3">Role</th>
                <th className="py-2.5 px-3">Department</th>
                <th className="py-2.5 px-3">Status</th>
                <th className="py-2.5 px-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50">
                  <td className="py-2.5 px-3 font-bold text-navy-900">{u.name}</td>
                  <td className="py-2.5 px-3 font-mono text-slate-600">{u.email}</td>
                  <td className="py-2.5 px-3">
                    <span
                      className={`px-2 py-0.5 rounded font-bold uppercase tracking-wider text-[10px] ${
                        u.role === 'ADMIN'
                          ? 'bg-purple-100 text-purple-800'
                          : 'bg-blue-100 text-blue-800'
                      }`}
                    >
                      {u.role}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-slate-600">{u.department || '—'}</td>
                  <td className="py-2.5 px-3">
                    <span className="text-emerald-700 font-semibold">Active</span>
                  </td>
                  <td className="py-2.5 px-3">
                    <button
                      onClick={() => handleDeleteUser(u.id)}
                      className="text-slate-400 hover:text-red-500 p-1 cursor-pointer"
                      title="Delete User"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};


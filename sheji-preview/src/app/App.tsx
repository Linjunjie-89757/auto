import { useState, useRef, useEffect } from 'react';
import {
  Home,
  FileText,
  Bug,
  Network,
  Monitor,
  Smartphone,
  Settings,
  Search,
  Plus,
  Bell,
  User,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Layers,
  Check,
} from 'lucide-react';
import TestCasePage from './components/TestCasePage';
import ApiTestPage from './components/ApiTestPage';
import SettingsPage from './components/SettingsPage';

interface Defect {
  id: string;
  title: string;
  status: '已提交' | '处理中' | '待修正';
  level: string;
  module: string;
  severity: string;
  assignee: string;
  tester: string;
  creator: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
  relatedCases: number;
}

const defects: Defect[] = [
  {
    id: 'BUG-001',
    title: '我们无法获取代码库调整',
    status: '已提交',
    level: 'P1',
    module: '高',
    severity: '验证',
    assignee: '-',
    tester: '-',
    creator: '组织管理员',
    createdAt: '2026-05-16 13:24',
    updatedBy: '组织管理员',
    updatedAt: '2026-05-16 16:55',
    relatedCases: 0,
  },
  {
    id: 'BUG-002',
    title: '开个标签知识产权指定不一致',
    status: '处理中',
    level: 'P2',
    module: '中',
    severity: '事中',
    assignee: '月中, 记录',
    tester: '张君',
    creator: '张君',
    createdAt: '2026-05-10 13:37',
    updatedBy: '张君',
    updatedAt: '2026-05-16 11:02',
    relatedCases: 1,
  },
  {
    id: 'BUG-051',
    title: '发行完成流量站该当开户代币',
    status: '处理中',
    level: 'P1',
    module: '高',
    severity: '超越',
    assignee: '洪升, Web, 图回',
    tester: '张君',
    creator: '张君',
    createdAt: '2026-05-10 13:37',
    updatedBy: '张君',
    updatedAt: '2026-05-10 13:37',
    relatedCases: 1,
  },
];

const workspaces = [
  { id: 'all', label: '全部', desc: '显示所有工作空间数据' },
  { id: 'account', label: '开户工作空间', desc: '开户相关测试项目' },
  { id: 'personal', label: '个人工作空间', desc: '个人测试与调试' },
];

export default function App() {
  const [activeTab, setActiveTab] = useState('defects');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [selectedWorkspace, setSelectedWorkspace] = useState(workspaces[0]);
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setWorkspaceOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const stats = [
    { label: '缺陷总数', value: 3, color: 'blue', status: '全部缺陷' },
    { label: '待处理', value: 1, color: 'purple', status: '已提交待处理' },
    { label: '处理中', value: 2, color: 'green', status: '正在处理中' },
    { label: '待修正', value: 0, color: 'orange', status: '等待修正确认' },
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case '已提交':
        return 'bg-blue-50 text-blue-700 border-blue-200';
      case '处理中':
        return 'bg-green-50 text-green-700 border-green-200';
      case '待修正':
        return 'bg-orange-50 text-orange-700 border-orange-200';
      default:
        return 'bg-gray-50 text-gray-700 border-gray-200';
    }
  };

  const getLevelColor = (level: string) => {
    switch (level) {
      case 'P1':
        return 'bg-red-100 text-red-700';
      case 'P2':
        return 'bg-yellow-100 text-yellow-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  const navItems = [
    { icon: Home, label: '工作台', id: 'dashboard' },
    { icon: FileText, label: '用例中心', id: 'testcases' },
    { icon: Bug, label: '缺陷管理', id: 'defects' },
    { icon: Network, label: '接口自动化', id: 'api' },
    { icon: Monitor, label: 'Web UI 自动化', id: 'webui' },
    { icon: Smartphone, label: 'APP 自动化', id: 'app' },
    { icon: Settings, label: '系统设置', id: 'settings' },
  ];

  return (
    <div className="size-full flex bg-gray-50">
      {/* Sidebar */}
      <aside className={`${sidebarCollapsed ? 'w-[60px]' : 'w-60'} bg-slate-900 flex flex-col transition-[width] duration-200 ease-in-out flex-shrink-0 overflow-hidden`}>

        {/* Logo + Collapse Toggle — 整行可点击切换 */}
        <div
          className="h-14 flex items-center px-3 border-b border-slate-800 flex-shrink-0 cursor-pointer hover:bg-slate-800 transition-colors group/logo"
          onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
        >
          <div className="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center flex-shrink-0">
            <span className="text-white font-bold text-sm">AT</span>
          </div>
          <div className={`flex-1 overflow-hidden ml-3 transition-opacity duration-150 ${sidebarCollapsed ? 'opacity-0' : 'opacity-100'}`}>
            <div className="font-semibold text-white text-sm whitespace-nowrap">Auto Test Hub</div>
            <div className="text-xs text-slate-400 whitespace-nowrap">全功能自动化测试平台</div>
          </div>
          <ChevronLeft className={`w-4 h-4 text-slate-400 flex-shrink-0 ml-1 transition-all duration-200 opacity-0 group-hover/logo:opacity-100 ${sidebarCollapsed ? 'rotate-180' : ''}`} />
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto overflow-x-hidden">
          {navItems.map((item, index) => {
            const isSettings = item.id === 'settings';
            return (
              <div key={item.id}>
                {isSettings && <div className="my-2 border-t border-slate-700/60" />}
                <div className="relative group/nav">
                  <button
                    onClick={() => setActiveTab(item.id)}
                    className={`w-full flex items-center px-2 py-2.5 rounded-lg text-sm transition-colors ${
                      activeTab === item.id
                        ? 'bg-blue-500 text-white font-medium'
                        : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'
                    }`}
                  >
                    {/* 图标固定大小和位置，不随状态变化 */}
                    <item.icon className="w-5 h-5 flex-shrink-0" />
                    <span className={`ml-3 whitespace-nowrap transition-opacity duration-150 ${sidebarCollapsed ? 'opacity-0' : 'opacity-100'}`}>
                      {item.label}
                    </span>
                  </button>
                  {/* Tooltip */}
                  <div className={`pointer-events-none absolute left-full top-1/2 -translate-y-1/2 ml-2 px-2.5 py-1.5 bg-slate-700 text-white text-xs rounded-lg shadow-lg whitespace-nowrap transition-opacity duration-150 z-50 opacity-0 ${sidebarCollapsed ? 'group-hover/nav:opacity-100' : ''}`}>
                    {item.label}
                    <div className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-slate-700" />
                  </div>
                </div>
              </div>
            );
          })}
        </nav>

        {/* User Profile */}
        <div className="px-2 pb-2 border-t border-slate-800 pt-2">
          <div className="relative group/user">
            <div className="flex items-center px-2 py-2 rounded-lg hover:bg-slate-800 transition-colors cursor-pointer">
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-sm font-medium flex-shrink-0">
                组
              </div>
              <div className={`ml-3 overflow-hidden transition-opacity duration-150 ${sidebarCollapsed ? 'opacity-0' : 'opacity-100'}`}>
                <div className="text-sm font-medium text-white whitespace-nowrap">组织管理员</div>
                <div className="text-xs text-slate-400 whitespace-nowrap">管理员 · 专业用户</div>
              </div>
            </div>
            <div className={`pointer-events-none absolute left-full top-1/2 -translate-y-1/2 ml-2 px-2.5 py-1.5 bg-slate-700 text-white text-xs rounded-lg shadow-lg whitespace-nowrap transition-opacity duration-150 z-50 opacity-0 ${sidebarCollapsed ? 'group-hover/user:opacity-100' : ''}`}>
              组织管理员
              <div className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-slate-700" />
            </div>
          </div>
        </div>

      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* Unified Top Header — workspace switcher + current page title + actions */}
        <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6 flex-shrink-0">
          {/* Left: Page title + workspace dropdown */}
          <div className="flex items-center gap-3 min-w-0">
            <h1 className="text-base font-semibold text-gray-900 flex-shrink-0">
              {navItems.find(n => n.id === activeTab)?.label ?? ''}
            </h1>

            <div className="w-px h-4 bg-gray-200 flex-shrink-0" />

            <div className="relative flex-shrink-0" ref={dropdownRef}>
              <button
                onClick={() => setWorkspaceOpen(!workspaceOpen)}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-gray-200 bg-gray-50 hover:bg-gray-100 transition-colors text-sm text-gray-600"
              >
                <Layers className="w-3.5 h-3.5 text-blue-500 flex-shrink-0" />
                <span className="truncate">{selectedWorkspace.label}</span>
                <ChevronDown className={`w-3.5 h-3.5 text-gray-400 flex-shrink-0 transition-transform ${workspaceOpen ? 'rotate-180' : ''}`} />
              </button>

              {workspaceOpen && (
                <div className="absolute top-full left-0 mt-1 w-56 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1 overflow-hidden">
                  <div className="px-3 py-2 border-b border-gray-100">
                    <span className="text-xs text-gray-400 font-medium uppercase tracking-wider">切换工作空间</span>
                  </div>
                  {workspaces.map((ws) => (
                    <button
                      key={ws.id}
                      onClick={() => { setSelectedWorkspace(ws); setWorkspaceOpen(false); }}
                      className="w-full flex items-start gap-3 px-3 py-2.5 hover:bg-gray-50 transition-colors text-left"
                    >
                      <div className="w-5 h-5 rounded bg-blue-100 flex items-center justify-center flex-shrink-0 mt-0.5">
                        <Layers className="w-3 h-3 text-blue-600" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm text-gray-800 font-medium truncate">{ws.label}</div>
                        <div className="text-xs text-gray-400 truncate">{ws.desc}</div>
                      </div>
                      {selectedWorkspace.id === ws.id && (
                        <Check className="w-4 h-4 text-blue-500 flex-shrink-0 mt-0.5" />
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Right: Notifications + User */}
          <div className="flex items-center gap-1 flex-shrink-0">
            <button className="p-2 text-gray-500 hover:bg-gray-100 rounded-lg transition-colors relative">
              <Bell className="w-4 h-4" />
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-red-500 rounded-full"></span>
            </button>
            <div className="w-px h-5 bg-gray-200 mx-1" />
            <button className="flex items-center gap-2 px-2 py-1.5 hover:bg-gray-100 rounded-lg transition-colors">
              <div className="w-6 h-6 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-xs font-medium flex-shrink-0">
                组
              </div>
              <span className="text-sm text-gray-700">超级管理员</span>
              <ChevronDown className="w-3.5 h-3.5 text-gray-400" />
            </button>
          </div>
        </header>

        {/* Page Content */}
        {activeTab === 'testcases' ? (
          <TestCasePage />
        ) : activeTab === 'api' ? (
          <ApiTestPage />
        ) : activeTab === 'settings' ? (
          <SettingsPage />
        ) : (
          <div className="flex-1 overflow-auto">

            <div className="p-8">
              {/* Stats Cards */}
              <div className="grid grid-cols-4 gap-6 mb-8">
                {stats.map((stat, index) => (
                  <div
                    key={index}
                    className="bg-white rounded-xl p-6 border border-gray-200 hover:shadow-lg transition-shadow"
                  >
                    <div className="flex items-start justify-between mb-4">
                      <div className="text-sm text-gray-600">{stat.label}</div>
                      <div
                        className={`w-2 h-2 rounded-full ${
                          stat.color === 'blue'
                            ? 'bg-blue-500'
                            : stat.color === 'purple'
                            ? 'bg-purple-500'
                            : stat.color === 'green'
                            ? 'bg-green-500'
                            : 'bg-orange-500'
                        }`}
                      />
                    </div>
                    <div className="text-3xl font-bold text-gray-900 mb-2">{stat.value}</div>
                    <div className="text-xs text-gray-500">{stat.status}</div>
                  </div>
                ))}
              </div>

              {/* Toolbar + Table */}
              <div className="bg-white rounded-xl border border-gray-200 mb-6">
                <div className="p-6 border-b border-gray-200 space-y-3">
                  {/* 第一行：搜索框 */}
                  <div className="relative w-80">
                    <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <input
                      type="text"
                      placeholder="搜索缺陷编号、说明..."
                      className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent w-full"
                    />
                  </div>
                  {/* 第二行：查询条件 + 新增按钮同高 */}
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 flex-wrap">
                      {[
                        { label: '已指派', options: ['全部', '已指派', '未指派'] },
                        { label: '优先级', options: ['全部', 'P0', 'P1', 'P2', 'P3'] },
                        { label: '严重程度', options: ['全部', '致命', '严重', '一般', '轻微'] },
                        { label: '处理人', options: ['全部', '张君', '王欣', '李文', '钱瑶'] },
                        { label: '所属空间', options: ['全部', '开户工作空间', '个人工作空间'] },
                      ].map((filter) => (
                        <div key={filter.label} className="relative flex-shrink-0">
                          <select className="appearance-none pl-3 pr-8 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent cursor-pointer hover:bg-gray-50 transition-colors">
                            <option value="">{filter.label}</option>
                            {filter.options.map((opt) => (
                              <option key={opt} value={opt}>{opt}</option>
                            ))}
                          </select>
                          <ChevronDown className="w-4 h-4 text-gray-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                        </div>
                      ))}
                    </div>
                    <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 transition-colors flex-shrink-0">
                      <Plus className="w-4 h-4" />
                      新增缺陷
                    </button>
                  </div>
                </div>

                {/* Table */}
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">缺陷编号</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">缺陷说明</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">状态</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">级别</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">模块</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">处理人</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">创建人</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">创建时间</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">更新时间</th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">关联用例</th>
                        <th className="sticky right-0 bg-gray-50 px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider border-l border-gray-200">
                          <div className="flex items-center justify-center gap-1">
                            <span>操作</span>
                            <Settings className="w-3.5 h-3.5" />
                          </div>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {defects.map((defect) => (
                        <tr key={defect.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-6 py-4 whitespace-nowrap">
                            <span className="text-sm font-medium text-blue-600">{defect.id}</span>
                          </td>
                          <td className="px-6 py-4">
                            <div className="text-sm text-gray-900 max-w-xs truncate">{defect.title}</div>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getStatusColor(defect.status)}`}>
                              {defect.status}
                            </span>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${getLevelColor(defect.level)}`}>
                              {defect.level}
                            </span>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{defect.module}</td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{defect.assignee}</td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{defect.creator}</td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{defect.createdAt}</td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{defect.updatedAt}</td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700 text-center">{defect.relatedCases}</td>
                          <td className="sticky right-0 bg-white px-6 py-4 whitespace-nowrap border-l border-gray-200">
                            <div className="flex items-center justify-center gap-3">
                              <button className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors">查看</button>
                              <button className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors">编辑</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
                  <div className="text-sm text-gray-700">共 3 条 / 1 页，10条/页</div>
                  <div className="flex items-center gap-2">
                    <button className="px-3 py-1 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50 transition-colors">上一页</button>
                    <button className="px-3 py-1 bg-blue-600 text-white rounded text-sm">1</button>
                    <button className="px-3 py-1 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50 transition-colors">下一页</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

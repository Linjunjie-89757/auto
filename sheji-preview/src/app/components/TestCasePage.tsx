import React, { useState, useRef, useEffect } from 'react';
import AiConfigPage from './AiConfigPage';
import {
  Search,
  Plus,
  ChevronRight,
  Settings,
  Folder,
  FolderOpen,
  MoreHorizontal,
  Pencil,
  Trash2,
  ClipboardList,
  Bug,
  Copy,
  X,
  ChevronLeft,
  FolderOpen as FolderOpenIcon,
} from 'lucide-react';

interface TestCase {
  id: string;
  name: string;
  status: '通过' | '失败';
  priority: string;
  validation: string;
  executor: string;
  module: string;
  path: string;
}

const testCases: TestCase[] = [
  {
    id: 'CASE-00131',
    name: '多次提现额度事例分别测试',
    status: '通过',
    priority: 'P2',
    validation: '半自动',
    executor: '王欣',
    module: '风控工作交流',
    path: '风控工作交流 / 事件处...',
  },
  {
    id: 'CASE-00130',
    name: '暂缓现场技改改建',
    status: '失败',
    priority: 'P0',
    validation: '半自动',
    executor: '钱瑶',
    module: '交易工作交流',
    path: '交易工作交流 / 事件处...',
  },
  {
    id: 'CASE-00129',
    name: '开户事故防护事项测试',
    status: '通过',
    priority: 'P1',
    validation: '半自动',
    executor: '李文',
    module: '开户工作交流',
    path: '开户工作交流 / 回归测...',
  },
  {
    id: 'CASE-00128',
    name: '开户统计正测试',
    status: '通过',
    priority: 'P0',
    validation: '半自动',
    executor: '陈晓',
    module: '开户工作交流',
    path: '开户工作交流 / 事件...',
  },
];

interface TreeNode {
  id: string;
  label: string;
  isWorkspace?: boolean;
  children?: TreeNode[];
}

const treeData: TreeNode[] = [
  {
    id: 'account',
    label: '开户工作交流',
    isWorkspace: true,
    children: [
      { id: 'account-regression', label: '回归测试', children: [] },
      { id: 'account-event', label: '事件处理', children: [] },
    ],
  },
  {
    id: 'trading',
    label: '交易工作交流',
    isWorkspace: true,
    children: [
      { id: 'trading-event', label: '事件处理', children: [] },
    ],
  },
  {
    id: 'risk',
    label: '风控工作交流',
    isWorkspace: true,
    children: [],
  },
];

function TreeItem({ node, level = 0 }: { node: TreeNode; level?: number }) {
  const [expanded, setExpanded] = useState(level === 0);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isWorkspace = node.isWorkspace || level === 0;
  const hasChildren = node.children && node.children.length > 0;

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    if (menuOpen) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  return (
    <div>
      <div
        className="group flex items-center py-1.5 hover:bg-gray-100 rounded-lg cursor-pointer text-sm transition-colors"
        style={{ paddingLeft: `${level * 14 + 8}px`, paddingRight: '6px' }}
        onClick={() => setExpanded(!expanded)}
      >
        <ChevronRight
          className={`w-3.5 h-3.5 mr-1 flex-shrink-0 transition-transform text-gray-400 ${expanded ? 'rotate-90' : ''}`}
        />
        {expanded ? (
          <FolderOpen className="w-4 h-4 text-blue-500 mr-2 flex-shrink-0" />
        ) : (
          <Folder className={`w-4 h-4 mr-2 flex-shrink-0 ${isWorkspace ? 'text-blue-400' : 'text-gray-400'}`} />
        )}
        <span className={`flex-1 truncate ${isWorkspace ? 'text-gray-800 font-medium' : 'text-gray-700'}`}>
          {node.label}
        </span>

        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity ml-1">
          <button
            onClick={(e) => { e.stopPropagation(); }}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500 transition-colors"
            title="新建子分类"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>

          {!isWorkspace && (
            <div className="relative" ref={menuRef}>
              <button
                onClick={(e) => { e.stopPropagation(); setMenuOpen(!menuOpen); }}
                className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500 transition-colors"
                title="更多操作"
              >
                <MoreHorizontal className="w-3.5 h-3.5" />
              </button>
              {menuOpen && (
                <div className="absolute left-0 top-6 w-32 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1 overflow-hidden">
                  <button
                    onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                  >
                    <Pencil className="w-3.5 h-3.5 text-gray-400" />
                    重命名
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    删除
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {expanded && (
        <div>
          {hasChildren
            ? node.children!.map((child) => (
                <TreeItem key={child.id} node={child} level={level + 1} />
              ))
            : (
              <div className="py-2 text-center" style={{ paddingLeft: `${level * 14 + 28}px` }}>
                <span className="text-xs text-gray-400">暂无内容</span>
              </div>
            )
          }
        </div>
      )}
    </div>
  );
}

const priorities = ['P0', 'P1', 'P2', 'P3'];

// 路径选择弹窗
interface PathNode { id: string; label: string; parent?: string; children?: PathNode[]; }
const pathTree: PathNode[] = [
  { id: 'account', label: '开户工作空间', children: [
    { id: 'account-regression', label: '回归测试' },
    { id: 'account-event', label: '事件处理' },
  ]},
  { id: 'trading', label: '交易工作空间', children: [
    { id: 'trading-event', label: '事件处理' },
  ]},
  { id: 'risk', label: '风控工作空间', children: [
    { id: 'risk-regression', label: '某容回归' },
  ]},
];

function PathTreeItem({ node, selectedId, onSelect, level = 0 }: {
  node: PathNode; selectedId: string | null; onSelect: (id: string, label: string) => void; level?: number;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children && node.children.length > 0;
  const isSelected = selectedId === node.id;
  return (
    <div>
      <div
        className={`flex items-center py-1.5 px-2 rounded-lg cursor-pointer text-sm transition-colors ${isSelected ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'}`}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={() => { onSelect(node.id, node.label); if (hasChildren) setExpanded(!expanded); }}
      >
        <ChevronRight className={`w-3.5 h-3.5 mr-1.5 flex-shrink-0 transition-transform ${hasChildren ? '' : 'opacity-0'} ${expanded && hasChildren ? 'rotate-90' : ''} text-gray-400`} />
        {expanded && hasChildren
          ? <FolderOpen className="w-4 h-4 mr-2 flex-shrink-0 text-blue-400" />
          : <Folder className="w-4 h-4 mr-2 flex-shrink-0 text-gray-400" />}
        <span className={`flex-1 truncate ${isSelected ? 'font-medium' : ''}`}>{node.label}</span>
      </div>
      {expanded && hasChildren && node.children!.map(child => (
        <PathTreeItem key={child.id} node={child} selectedId={selectedId} onSelect={onSelect} level={level + 1} />
      ))}
    </div>
  );
}

function PathModal({ currentPath, onConfirm, onClose }: {
  currentPath: string; onConfirm: (path: string) => void; onClose: () => void;
}) {
  const [selectedId, setSelectedId] = useState<string | null>('risk-regression');
  const [selectedLabel, setSelectedLabel] = useState(currentPath);
  const [search, setSearch] = useState('');

  const handleSelect = (id: string, label: string) => {
    setSelectedId(id);
    // 找父级拼路径
    for (const ws of pathTree) {
      if (ws.id === id) { setSelectedLabel(ws.label); return; }
      for (const child of ws.children ?? []) {
        if (child.id === id) { setSelectedLabel(`${ws.label} / ${child.label}`); return; }
      }
    }
    setSelectedLabel(label);
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/30" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[420px] flex flex-col overflow-hidden" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <span className="text-base font-semibold text-gray-900">选择保存路径</span>
          <button onClick={onClose} className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-gray-100 text-gray-400 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 当前路径 */}
        <div className="px-5 py-3 bg-gray-50 border-b border-gray-100">
          <p className="text-xs text-gray-400">当前保存路径</p>
          <p className="text-sm text-gray-700 mt-0.5">{currentPath}</p>
        </div>

        {/* 搜索 */}
        <div className="px-5 py-3 border-b border-gray-100">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="搜索目录名称"
              className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white"
            />
          </div>
        </div>

        {/* 树 */}
        <div className="flex-1 overflow-y-auto px-3 py-2 min-h-[200px] max-h-[260px]">
          {pathTree.map(node => (
            <PathTreeItem key={node.id} node={node} selectedId={selectedId} onSelect={handleSelect} />
          ))}
        </div>

        {/* 已选路径 */}
        <div className="px-5 py-3 border-t border-gray-100 bg-gray-50">
          <p className="text-xs text-gray-400">已选路径</p>
          <p className="text-sm text-blue-600 mt-0.5 font-medium">{selectedLabel || '未选择'}</p>
        </div>

        {/* 操作 */}
        <div className="flex items-center justify-end gap-2 px-5 py-3 border-t border-gray-100">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">取消</button>
          <button
            onClick={() => { onConfirm(selectedLabel); onClose(); }}
            className="flex items-center gap-1.5 px-4 py-2 text-sm text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <FolderOpenIcon className="w-3.5 h-3.5" />
            确认修改
          </button>
        </div>
      </div>
    </div>
  );
}

function EditDrawer({
  tc, index, total, onPrev, onNext, onClose,
}: {
  tc: TestCase; index: number; total: number;
  onPrev: () => void; onNext: () => void; onClose: () => void;
}) {
  const [name, setName] = useState(tc.name);
  const [priority, setPriority] = useState(tc.priority);
  const [modulePath, setModulePath] = useState('风控工作空间 / 某容回归');
  const [precondition, setPrecondition] = useState('Chrome/Edge 可用');
  const [steps, setSteps] = useState('执行登录回归');
  const [expected, setExpected] = useState('测试通过');
  const [showPathModal, setShowPathModal] = useState(false);

  useEffect(() => { setName(tc.name); setPriority(tc.priority); }, [tc]);

  return (
    <>
      <div className="fixed inset-0 bg-black/20 z-40" onClick={onClose} />
      <div className="fixed top-0 right-0 h-full w-[480px] bg-white z-50 flex flex-col shadow-2xl">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h2 className="text-base font-semibold text-gray-900">编辑用例</h2>
            <p className="text-xs text-gray-400 mt-0.5">{tc.id}</p>
          </div>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 text-gray-400 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form */}
        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              用例名称 <span className="text-red-500">*</span>
            </label>
            <input type="text" value={name} onChange={e => setName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>

          {/* 用例模块 — 文件夹图标打开路径选择 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">用例模块</label>
            <div className="flex items-center gap-2">
              <div className="flex-1 flex items-center px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white">
                <span className="flex-1 truncate">{modulePath}</span>
              </div>
              <button
                onClick={() => setShowPathModal(true)}
                className="w-9 h-9 flex items-center justify-center border border-gray-300 rounded-lg hover:bg-gray-50 hover:border-blue-400 text-gray-400 hover:text-blue-500 transition-colors flex-shrink-0"
                title="选择路径"
              >
                <Folder className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">优先级</label>
            <div className="inline-flex items-center bg-gray-100 rounded-lg p-1 gap-0.5">
              {priorities.map(p => (
                <button key={p} onClick={() => setPriority(p)}
                  className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${priority === p ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
                  {p}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">前置条件</label>
            <textarea value={precondition} onChange={e => setPrecondition(e.target.value)} rows={3}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">测试步骤</label>
            <textarea value={steps} onChange={e => setSteps(e.target.value)} rows={4} placeholder="请输入测试步骤..."
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">预期结果</label>
            <textarea value={expected} onChange={e => setExpected(e.target.value)} rows={4} placeholder="请输入预期结果..."
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3.5 border-t border-gray-100 bg-gray-50 flex-shrink-0">
          {/* 上一条 / 下一条 — 文字按钮 */}
          <div className="flex items-center gap-1">
            <button onClick={onPrev} disabled={index === 0}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <ChevronLeft className="w-3.5 h-3.5" />
              上一条
            </button>
            <span className="px-2.5 py-1.5 text-xs text-gray-400 tabular-nums">{index + 1} / {total}</span>
            <button onClick={onNext} disabled={index === total - 1}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              下一条
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-100 transition-colors">取消</button>
            <button onClick={onClose} className="px-4 py-2 text-sm text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors font-medium">保存</button>
          </div>
        </div>
      </div>

      {showPathModal && (
        <PathModal
          currentPath={modulePath}
          onConfirm={path => setModulePath(path)}
          onClose={() => setShowPathModal(false)}
        />
      )}
    </>
  );
}

export default function TestCasePage() {
  const [activeTab, setActiveTab] = useState('manage');
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; right: number } | null>(null);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const rowMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (rowMenuRef.current && !rowMenuRef.current.contains(e.target as Node)) {
        setOpenMenuId(null);
        setMenuPos(null);
      }
    }
    if (openMenuId) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [openMenuId]);

  const handleMenuOpen = (e: React.MouseEvent<HTMLButtonElement>, id: string) => {
    if (openMenuId === id) {
      setOpenMenuId(null);
      setMenuPos(null);
      return;
    }
    const rect = e.currentTarget.getBoundingClientRect();
    setMenuPos({ top: rect.bottom + 4, right: window.innerWidth - rect.right });
    setOpenMenuId(id);
  };

  const tabs = [
    { id: 'manage', label: '用例管理' },
    { id: 'ai-generate', label: 'AI 用例生成' },
    { id: 'ai-history', label: 'AI 生成记录' },
    { id: 'ai-config', label: 'AI 配置' },
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case '通过': return 'bg-green-50 text-green-700 border-green-200';
      case '失败': return 'bg-red-50 text-red-700 border-red-200';
      default: return 'bg-gray-50 text-gray-700 border-gray-200';
    }
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'P0': return 'bg-red-100 text-red-700';
      case 'P1': return 'bg-orange-100 text-orange-700';
      case 'P2': return 'bg-yellow-100 text-yellow-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const menuItems = [
    { icon: ClipboardList, label: '评审', color: 'text-gray-700', danger: false },
    { icon: Bug, label: '提缺陷', color: 'text-gray-700', danger: false },
    { icon: Copy, label: '复制', color: 'text-gray-700', danger: false },
    { icon: Trash2, label: '删除', color: 'text-red-600', danger: true },
  ] as { icon: React.ElementType; label: string; color: string; danger: boolean }[];

  return (
    <div className="size-full flex flex-col bg-gray-50">

      {/* Tabs — 分段控件，对齐接口自动化风格 */}
      <div className="bg-white border-b border-gray-200 px-6 flex items-center h-12 flex-shrink-0">
        <div className="inline-flex items-center bg-gray-100 rounded-lg p-1 gap-0.5">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                activeTab === tab.id
                  ? 'bg-white text-gray-900 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      {activeTab === 'ai-config' ? <AiConfigPage /> : (
      <>
      <div className="flex-1 flex overflow-hidden">

        {/* Left Sidebar — 对齐接口自动化目录树风格 */}
        <aside className="w-64 bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
          <div className="px-3 pt-3 pb-2">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="搜索分类名称"
                className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-2 pb-2" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
            <div className="flex items-center justify-between mb-1 px-1 py-1 border-b border-gray-100">
              <span className="text-sm font-semibold text-gray-700">用例分类</span>
              <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5">
                {treeData.length}
              </span>
            </div>
            <div className="space-y-0.5 mt-1">
              {treeData.map((node) => (
                <TreeItem key={node.id} node={node} />
              ))}
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 overflow-auto p-6">
          <div className="bg-white rounded-xl border border-gray-200">

            {/* Toolbar */}
            <div className="p-5 border-b border-gray-200 space-y-3">
              <div className="flex items-center gap-3 flex-wrap">
                <div className="relative">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    placeholder="搜索用例编号、名称..."
                    className="pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent w-64"
                  />
                </div>
                {[
                  { label: '优先级', options: ['P0', 'P1', 'P2'] },
                  { label: '状态', options: ['通过', '失败'] },
                  { label: '验证方式', options: ['半自动', '全自动', '手动'] },
                  { label: '执行人', options: ['王欣', '钱瑶', '李文', '陈晓'] },
                  { label: '创建人', options: ['张君', '王欣'] },
                  { label: '所属空间', options: ['开户工作交流', '交易工作交流', '风控工作交流'] },
                ].map((f) => (
                  <div key={f.label} className="relative flex-shrink-0">
                    <select className="appearance-none pl-3 pr-7 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer hover:bg-gray-50 transition-colors">
                      <option value="">{f.label}</option>
                      {f.options.map((o) => <option key={o}>{o}</option>)}
                    </select>
                    <ChevronRight className="w-3.5 h-3.5 text-gray-400 absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none rotate-90" />
                  </div>
                ))}
                <button className="flex items-center gap-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 transition-colors px-4 py-2 ml-auto flex-shrink-0">
                  <Plus className="w-4 h-4" />
                  新增用例
                </button>
              </div>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    {['用例编号', '用例名称', '状态', '优先级', '验证方式', '执行人', '所属空间', '用例路径'].map((h) => (
                      <th key={h} className="px-5 py-3 text-left text-xs font-medium text-gray-500">
                        {h}
                      </th>
                    ))}
                    <th className="sticky right-0 bg-gray-50 px-5 py-3 text-center text-xs font-medium text-gray-500 border-l border-gray-200">
                      <div className="flex items-center justify-center gap-1">
                        <span>操作</span>
                        <Settings className="w-3.5 h-3.5" />
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {testCases.map((tc) => (
                    <tr key={tc.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span className="text-sm font-medium text-blue-600">{tc.id}</span>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="text-sm text-gray-900 max-w-xs truncate">{tc.name}</div>
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getStatusColor(tc.status)}`}>
                          {tc.status}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${getPriorityColor(tc.priority)}`}>
                          {tc.priority}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap text-sm text-gray-700">{tc.validation}</td>
                      <td className="px-5 py-3.5 whitespace-nowrap text-sm text-gray-700">{tc.executor}</td>
                      <td className="px-5 py-3.5 whitespace-nowrap text-sm text-gray-700">{tc.module}</td>
                      <td className="px-5 py-3.5 whitespace-nowrap text-sm text-gray-500">{tc.path}</td>
                      <td className="sticky right-0 bg-white px-5 py-3.5 whitespace-nowrap border-l border-gray-200">
                        <div className="flex items-center justify-center gap-2">
                          <button
                            onClick={() => setEditingIndex(testCases.findIndex(t => t.id === tc.id))}
                            className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors"
                          >编辑</button>
                          <button className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors">
                            执行
                          </button>
                          <button
                            onClick={(e) => handleMenuOpen(e, tc.id)}
                            className="w-6 h-6 flex items-center justify-center rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
                          >
                            <MoreHorizontal className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="px-5 py-3.5 border-t border-gray-200 flex items-center justify-between">
              <div className="text-sm text-gray-500">共 4 条 / 1 页</div>
              <div className="flex items-center gap-2">
                <button className="px-3 py-1 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50 transition-colors">上一页</button>
                <button className="px-3 py-1 bg-blue-600 text-white rounded text-sm">1</button>
                <button className="px-3 py-1 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50 transition-colors">下一页</button>
              </div>
            </div>
          </div>
        </main>
      </div>

      {/* 编辑抽屉 */}
      {editingIndex !== null && (
        <EditDrawer
          tc={testCases[editingIndex]}
          index={editingIndex}
          total={testCases.length}
          onPrev={() => setEditingIndex(i => Math.max(0, (i ?? 0) - 1))}
          onNext={() => setEditingIndex(i => Math.min(testCases.length - 1, (i ?? 0) + 1))}
          onClose={() => setEditingIndex(null)}
        />
      )}

      {/* 行操作下拉菜单 — fixed 定位，不受 overflow 裁剪 */}
      {openMenuId && menuPos && (
        <div
          ref={rowMenuRef}
          className="fixed w-32 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1 overflow-hidden"
          style={{ top: menuPos.top, right: menuPos.right }}
        >
          {menuItems.map(({ icon: Icon, label, color, danger }) => (
            <button
              key={label}
              onClick={() => { setOpenMenuId(null); setMenuPos(null); }}
              className={`w-full flex items-center gap-2 px-3 py-2 text-sm ${color} ${danger ? 'hover:bg-red-50' : 'hover:bg-gray-50'} transition-colors`}
            >
              <Icon className="w-3.5 h-3.5 opacity-60" />
              {label}
            </button>
          ))}
        </div>
      )}
      </>
      )}
    </div>
  );
}

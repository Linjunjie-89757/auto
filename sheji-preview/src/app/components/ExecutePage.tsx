import { useState, useRef, useEffect } from 'react';
import {
  Search, Plus, ChevronRight, ChevronDown, Folder, FolderOpen,
  X, MoreHorizontal, Pencil, Trash2, Check, Play, Save,
  GripVertical, Link2, Layers, Bell, Clock, AlignLeft,
  Calendar, GitBranch, BarChart2, Zap, Settings2,
} from 'lucide-react';

// ── Tree ──────────────────────────────────────────────────────────────────

interface SuiteTreeNode {
  id: string;
  label: string;
  isWorkspace?: boolean;
  count?: number;
  children?: SuiteTreeNode[];
}

const suiteTreeData: SuiteTreeNode[] = [
  {
    id: 'account', label: '开户工作空间', isWorkspace: true, count: 4,
    children: [
      {
        id: 'smoke', label: '冒烟测试', count: 2,
        children: [
          { id: 'smoke-order', label: '订单冒烟', count: 0, children: [] },
          { id: 'smoke-account', label: '开户冒烟', count: 0, children: [] },
        ],
      },
      {
        id: 'regress', label: '回归测试', count: 2,
        children: [
          { id: 'reg-login', label: '登录回归', count: 0, children: [] },
          { id: 'reg-full', label: '全量回归', count: 0, children: [] },
        ],
      },
    ],
  },
  { id: 'trading', label: '交易工作空间', isWorkspace: true, count: 0, children: [] },
];

function SuiteTreeItem({ node, level = 0, activeId, onSelect }: {
  node: SuiteTreeNode; level?: number;
  activeId: string; onSelect: (id: string) => void;
}) {
  const [expanded, setExpanded] = useState(level <= 1);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isWorkspace = !!node.isWorkspace;
  const hasChildren = (node.children?.length ?? 0) > 0;
  const isLeaf = !hasChildren && !isWorkspace && level >= 2;
  const isActive = activeId === node.id;

  useEffect(() => {
    function h(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    }
    if (menuOpen) document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [menuOpen]);

  return (
    <div>
      <div
        className={`group flex items-center py-1.5 rounded-lg cursor-pointer text-sm transition-colors ${
          isActive ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-100 text-gray-700'
        }`}
        style={{ paddingLeft: `${level * 14 + 8}px`, paddingRight: '6px' }}
        onClick={() => {
          if (!isLeaf) setExpanded(!expanded);
          if (isLeaf) onSelect(node.id);
        }}
      >
        <ChevronRight
          className={`w-3.5 h-3.5 mr-1 flex-shrink-0 transition-transform ${
            isLeaf ? 'opacity-0 pointer-events-none' : 'text-gray-400'
          } ${expanded ? 'rotate-90' : ''}`}
        />
        {expanded && hasChildren
          ? <FolderOpen className="w-4 h-4 text-blue-500 mr-2 flex-shrink-0" />
          : <Folder className={`w-4 h-4 mr-2 flex-shrink-0 ${isWorkspace ? 'text-blue-400' : isActive ? 'text-blue-500' : 'text-gray-400'}`} />
        }
        <span className={`flex-1 truncate ${isWorkspace ? 'font-medium text-gray-800' : isActive ? 'font-medium' : ''}`}>
          {node.label}
        </span>
        {node.count !== undefined && (
          <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5 mr-1 group-hover:opacity-0 transition-opacity">
            {node.count}
          </span>
        )}
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button onClick={(e) => e.stopPropagation()} className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500">
            <Plus className="w-3.5 h-3.5" />
          </button>
          {!isWorkspace && (
            <div className="relative" ref={menuRef}>
              <button
                onClick={(e) => { e.stopPropagation(); setMenuOpen(!menuOpen); }}
                className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500"
              >
                <MoreHorizontal className="w-3.5 h-3.5" />
              </button>
              {menuOpen && (
                <div className="absolute left-0 top-6 w-32 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1">
                  <button onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <Pencil className="w-3.5 h-3.5 text-gray-400" />重命名
                  </button>
                  <button onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50">
                    <Trash2 className="w-3.5 h-3.5" />删除
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
            ? node.children!.map(c => <SuiteTreeItem key={c.id} node={c} level={level + 1} activeId={activeId} onSelect={onSelect} />)
            : (
              <div className="py-1.5 text-center" style={{ paddingLeft: `${level * 14 + 28}px` }}>
                <span className="text-xs text-gray-400">暂无套件</span>
              </div>
            )
          }
        </div>
      )}
    </div>
  );
}

// ── Helpers ───────────────────────────────────────────────────────────────

const methodColor: Record<string, string> = {
  GET: 'text-green-600',
  POST: 'text-blue-600',
  PUT: 'text-orange-500',
  DELETE: 'text-red-500',
  PATCH: 'text-purple-600',
};
const methodBadge: Record<string, string> = {
  GET: 'bg-green-50 text-green-700 border-green-200',
  POST: 'bg-blue-50 text-blue-700 border-blue-200',
  PUT: 'bg-orange-50 text-orange-700 border-orange-200',
  DELETE: 'bg-red-50 text-red-700 border-red-200',
  PATCH: 'bg-purple-50 text-purple-700 border-purple-200',
};

const priorityColor: Record<string, string> = {
  P0: 'bg-red-100 text-red-700 border-red-200',
  P1: 'bg-orange-100 text-orange-700 border-orange-200',
  P2: 'bg-yellow-100 text-yellow-700 border-yellow-200',
  P3: 'bg-gray-100 text-gray-600 border-gray-200',
};

function FieldSelect({ label, value, onChange, options }: {
  label: string; value: string; onChange: (v: string) => void; options: string[];
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function h(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [open]);

  return (
    <div className="space-y-2">
      <label className="text-xs text-gray-500">{label}</label>
      <div className="relative" ref={ref}>
        <button
          onClick={() => setOpen(!open)}
          className="w-full flex items-center justify-between px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-gray-300 transition-colors"
        >
          <span>{value}</span>
          <ChevronDown className={`w-3.5 h-3.5 text-gray-400 transition-transform flex-shrink-0 ${open ? 'rotate-180' : ''}`} />
        </button>
        {open && (
          <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1">
            {options.map(opt => (
              <button key={opt} onClick={() => { onChange(opt); setOpen(false); }}
                className="w-full flex items-center justify-between px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <span>{opt}</span>
                {value === opt && <Check className="w-3.5 h-3.5 text-blue-500" />}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Case row ──────────────────────────────────────────────────────────────

type CaseType = 'api' | 'scene';
interface CaseItem {
  id: string;
  type: CaseType;
  name: string;
  method?: string;
  url?: string;
  desc?: string;
}

function CaseRow({ item, onRemove }: { item: CaseItem; onRemove: () => void }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function h(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    }
    if (menuOpen) document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [menuOpen]);

  return (
    <div className="group flex items-center gap-3 px-4 py-3 border-b border-gray-100 hover:bg-blue-50/30 transition-colors">
      <GripVertical className="w-4 h-4 text-gray-300 group-hover:text-gray-400 flex-shrink-0 cursor-grab" />

      {/* type badge */}
      {item.type === 'api' ? (
        <span className="flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-600 border border-blue-200 rounded text-xs font-medium flex-shrink-0">
          <Link2 className="w-3 h-3" /> 接口
        </span>
      ) : (
        <span className="flex items-center gap-1 px-2 py-0.5 bg-purple-50 text-purple-600 border border-purple-200 rounded text-xs font-medium flex-shrink-0">
          <Zap className="w-3 h-3" /> 场景
        </span>
      )}

      {/* name */}
      <span className="text-sm text-gray-800 font-medium flex-shrink-0 w-32 truncate">{item.name}</span>

      {/* method + url or desc */}
      {item.type === 'api' && item.method ? (
        <>
          <span className={`px-2 py-0.5 rounded text-xs font-semibold border flex-shrink-0 ${methodBadge[item.method]}`}>
            {item.method}
          </span>
          <span className="text-xs text-gray-400 font-mono flex-1 truncate">{item.url}</span>
        </>
      ) : (
        <span className="text-xs text-gray-400 flex-1 truncate">{item.desc}</span>
      )}

      {/* actions */}
      <div className="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
        <div className="relative" ref={menuRef}>
          <button onClick={() => setMenuOpen(!menuOpen)}
            className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-gray-200 text-gray-400 transition-colors">
            <MoreHorizontal className="w-4 h-4" />
          </button>
          {menuOpen && (
            <div className="absolute right-0 top-8 w-28 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1">
              <button className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
                <Pencil className="w-3.5 h-3.5 text-gray-400" />编辑
              </button>
              <button onClick={() => { setMenuOpen(false); onRemove(); }}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50">
                <Trash2 className="w-3.5 h-3.5" />移除
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Suite tab ──────────────────────────────────────────────────────────────

interface SuiteTab { id: string; name: string; }

const subTabs = [
  { id: 'arrange', label: '编排', icon: AlignLeft },
  { id: 'cron', label: '定时任务', icon: Calendar },
  { id: 'cicd', label: 'CI/CD', icon: GitBranch },
  { id: 'result', label: '运行结果', icon: BarChart2 },
];

const mockCases: CaseItem[] = [
  { id: 'c1', type: 'api', name: '用户登录接口', method: 'POST', url: '/api/auth/login' },
  { id: 'c2', type: 'scene', name: '开户登录流程', desc: '包含 4 个步骤' },
  { id: 'c3', type: 'api', name: '获取用户信息', method: 'GET', url: '/api/user/profile' },
];

const priorities = ['P0', 'P1', 'P2', 'P3'];
const envOptions = ['开户-UAT', '开发环境', '测试环境', '预发布环境'];
const runModes = ['串行', '并行'];
const runOnOptions = ['本机', '远程机器', 'Docker'];

// ── Main ──────────────────────────────────────────────────────────────────

export default function ExecutePage() {
  const [activeTreeId, setActiveTreeId] = useState('smoke-order');
  const [suiteTabs, setSuiteTabs] = useState<SuiteTab[]>([
    { id: 'all', name: '全部套件' },
    { id: 'smoke-order', name: '订单冒烟' },
  ]);
  const [activeSuiteTab, setActiveSuiteTab] = useState('smoke-order');
  const [activeSubTab, setActiveSubTab] = useState('arrange');
  const [cases, setCases] = useState<CaseItem[]>(mockCases);

  // Suite meta
  const [suiteName, setSuiteName] = useState('订单冒烟');
  const [priority, setPriority] = useState('P2');
  const [priorityOpen, setPriorityOpen] = useState(false);
  const [desc, setDesc] = useState('');
  const priorityRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function h(e: MouseEvent) {
      if (priorityRef.current && !priorityRef.current.contains(e.target as Node)) setPriorityOpen(false);
    }
    if (priorityOpen) document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [priorityOpen]);

  // Right panel
  const [runEnv, setRunEnv] = useState('开户-UAT');
  const [runMode, setRunMode] = useState('串行');
  const [runOn, setRunOn] = useState('本机');
  const [notify, setNotify] = useState(true);

  const addTab = () => {
    const id = Date.now().toString();
    setSuiteTabs(prev => [...prev, { id, name: '新建套件' }]);
    setActiveSuiteTab(id);
    setCases([]);
    setSuiteName('新建套件');
    setDesc('');
  };

  const closeTab = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const rest = suiteTabs.filter(t => t.id !== id);
    setSuiteTabs(rest);
    if (activeSuiteTab === id && rest.length > 0) setActiveSuiteTab(rest[rest.length - 1].id);
  };

  const removeCase = (id: string) => setCases(prev => prev.filter(c => c.id !== id));

  const addApiCase = () => {
    setCases(prev => [...prev, {
      id: Date.now().toString(),
      type: 'api',
      name: '新建接口用例',
      method: 'GET',
      url: '/api/new/endpoint',
    }]);
  };

  const addSceneCase = () => {
    setCases(prev => [...prev, {
      id: Date.now().toString(),
      type: 'scene',
      name: '新建场景用例',
      desc: '包含 0 个步骤',
    }]);
  };

  return (
    <div className="flex-1 flex overflow-hidden min-w-0">

      {/* ── Left sidebar ── */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
        <div className="px-4 pt-4 pb-3">
          <button onClick={addTab}
            className="w-full flex items-center justify-center gap-2 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
            <Plus className="w-4 h-4" /> 新建套件
          </button>
        </div>

        <div className="px-4 pb-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input type="text" placeholder="搜索套件名称"
              className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-3" style={{ scrollbarWidth: 'none' }}>
          <div className="flex items-center justify-between mb-2 px-1 py-1 border-b border-gray-100">
            <span className="text-sm font-semibold text-gray-700">测试套件</span>
            <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5">4</span>
          </div>
          <div className="space-y-0.5">
            {suiteTreeData.map(node => (
              <SuiteTreeItem key={node.id} node={node} activeId={activeTreeId} onSelect={(id) => {
                setActiveTreeId(id);
                const found = suiteTreeData.flatMap(n => n.children ?? []).flatMap(n => n.children ?? []).find(n => n.id === id);
                if (found && !suiteTabs.find(t => t.id === id)) {
                  setSuiteTabs(prev => [...prev, { id, name: found.label }]);
                }
                setActiveSuiteTab(id);
              }} />
            ))}
          </div>
        </div>
      </aside>

      {/* ── Center + Right ── */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">

        {/* Suite tab bar */}
        <div className="bg-white border-b border-gray-200 flex-shrink-0 flex items-center min-w-0">
          <style>{`.suite-tabs::-webkit-scrollbar{display:none}`}</style>
          <div className="suite-tabs flex items-center overflow-x-auto flex-1 min-w-0" style={{ scrollbarWidth: 'none' }}>
            {suiteTabs.map(tab => (
              <button key={tab.id} onClick={() => setActiveSuiteTab(tab.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm border-r border-gray-200 flex-shrink-0 transition-colors group ${
                  activeSuiteTab === tab.id
                    ? 'bg-white text-gray-800 border-b-2 border-b-blue-500'
                    : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
                }`}>
                <span>{tab.name}</span>
                {tab.id !== 'all' && (
                  <span onClick={(e) => closeTab(tab.id, e)}
                    className="w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 opacity-0 group-hover:opacity-100 transition-opacity">
                    <X className="w-3 h-3" />
                  </span>
                )}
              </button>
            ))}
          </div>
          <div className="flex items-center flex-shrink-0 border-l border-gray-200">
            <button onClick={addTab} className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
              <Plus className="w-4 h-4" />
            </button>
            <button className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
              <MoreHorizontal className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="flex-1 flex overflow-hidden">

          {/* ── Main content ── */}
          <div className="flex-1 flex flex-col overflow-hidden min-w-0 bg-white">

            {/* Sub-tabs */}
            <div className="border-b border-gray-200 px-4 flex-shrink-0">
              <div className="flex">
                {subTabs.map(tab => (
                  <button key={tab.id} onClick={() => setActiveSubTab(tab.id)}
                    className={`flex items-center gap-1.5 px-3 py-2.5 text-sm transition-colors border-b-2 ${
                      activeSubTab === tab.id
                        ? 'border-blue-500 text-blue-600 font-medium'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                    }`}>
                    <tab.icon className="w-3.5 h-3.5" />
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Arrange tab content */}
            {activeSubTab === 'arrange' && (
              <div className="flex-1 flex flex-col overflow-hidden">

                {/* Suite header */}
                <div className="px-6 py-4 border-b border-gray-100 flex-shrink-0 space-y-2">
                  {/* Name row */}
                  <div className="flex items-center gap-3">
                    {/* Priority badge dropdown */}
                    <div className="relative flex-shrink-0" ref={priorityRef}>
                      <button onClick={() => setPriorityOpen(!priorityOpen)}
                        className={`flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold border ${priorityColor[priority]}`}>
                        {priority}
                        <ChevronDown className={`w-3 h-3 transition-transform ${priorityOpen ? 'rotate-180' : ''}`} />
                      </button>
                      {priorityOpen && (
                        <div className="absolute top-full left-0 mt-1 w-24 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1">
                          {priorities.map(p => (
                            <button key={p} onClick={() => { setPriority(p); setPriorityOpen(false); }}
                              className="w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-gray-50 transition-colors">
                              <span className={`text-xs font-semibold ${priorityColor[p].split(' ')[1]}`}>{p}</span>
                              {priority === p && <Check className="w-3.5 h-3.5 text-blue-500" />}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    <input
                      value={suiteName}
                      onChange={e => setSuiteName(e.target.value)}
                      className="text-lg font-semibold text-gray-900 focus:outline-none border-b border-transparent hover:border-gray-300 focus:border-blue-400 bg-transparent transition-colors flex-1 min-w-0"
                    />
                  </div>

                  {/* Description */}
                  <input
                    value={desc}
                    onChange={e => setDesc(e.target.value)}
                    placeholder="添加描述..."
                    className="w-full text-sm text-gray-500 focus:outline-none bg-transparent placeholder:text-gray-400 hover:placeholder:text-gray-500"
                  />

                  {/* Meta */}
                  <div className="flex items-center gap-1 text-xs text-gray-400">
                    <Clock className="w-3 h-3" />
                    <span>组织管理员</span>
                    <span>更新于 2 分钟前</span>
                    <span className="mx-1">·</span>
                    <span>创建于 5 分钟前</span>
                  </div>
                </div>

                {/* Cases list */}
                <div className="flex-1 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
                  {cases.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full gap-4">
                      <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center">
                        <Layers className="w-6 h-6 text-gray-400" />
                      </div>
                      <p className="text-sm text-gray-400">添加单接口用例或场景用例</p>
                      <div className="flex items-center gap-3">
                        <button onClick={addApiCase}
                          className="flex items-center gap-1.5 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors">
                          <Plus className="w-4 h-4" /> 添加单接口用例
                        </button>
                        <button onClick={addSceneCase}
                          className="flex items-center gap-1.5 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors">
                          <Plus className="w-4 h-4" /> 添加场景用例
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div>
                      {/* toolbar */}
                      <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-100 bg-gray-50">
                        <span className="text-sm text-gray-500">共 {cases.length} 个内容</span>
                        <div className="flex items-center gap-2">
                          <button onClick={addApiCase}
                            className="flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-100 transition-colors">
                            <Plus className="w-3.5 h-3.5" /> 接口用例
                          </button>
                          <button onClick={addSceneCase}
                            className="flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-100 transition-colors">
                            <Plus className="w-3.5 h-3.5" /> 场景用例
                          </button>
                        </div>
                      </div>
                      {cases.map(item => (
                        <CaseRow key={item.id} item={item} onRemove={() => removeCase(item.id)} />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {activeSubTab !== 'arrange' && (
              <div className="flex-1 flex items-center justify-center bg-gray-50">
                <p className="text-sm text-gray-400">
                  {subTabs.find(t => t.id === activeSubTab)?.label} 内容区
                </p>
              </div>
            )}
          </div>

          {/* ── Right: Run config panel ── */}
          <div className="w-72 bg-white flex-shrink-0 p-3 flex flex-col">
            <div className="bg-gray-50 rounded-2xl border border-gray-200 shadow-sm flex flex-col flex-1 overflow-hidden">

              {/* 顶部：环境 + 运行/保存 — 与场景页一致 */}
              <div className="px-4 pt-4 pb-3 border-b border-gray-100 flex-shrink-0">
                <div className="flex items-center gap-2 mb-3">
                  <div className="flex-1">
                    <FieldSelect label="" value={runEnv} onChange={setRunEnv} options={envOptions} />
                  </div>
                  <button className="w-8 h-9 flex items-center justify-center border border-gray-200 rounded-lg hover:bg-gray-100 text-gray-400 flex-shrink-0 bg-white">
                    <Settings2 className="w-4 h-4" />
                  </button>
                </div>
                <div className="flex items-center gap-2">
                  <button className="flex-1 flex items-center justify-center gap-1.5 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
                    <Play className="w-3.5 h-3.5" /> 运行
                  </button>
                  <button className="flex-1 flex items-center justify-center gap-1.5 py-2 border border-gray-200 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-100 transition-colors">
                    <Save className="w-3.5 h-3.5" /> 保存
                  </button>
                </div>
              </div>

              {/* Config fields */}
              <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5" style={{ scrollbarWidth: 'none' }}>

                <FieldSelect label="运行模式" value={runMode} onChange={setRunMode} options={runModes} />

                <FieldSelect label="运行于" value={runOn} onChange={setRunOn} options={runOnOptions} />

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Bell className="w-4 h-4 text-gray-400" />
                    <span className="text-sm text-gray-700">通知</span>
                  </div>
                  <button
                    onClick={() => setNotify(!notify)}
                    className={`relative w-10 h-5 rounded-full transition-colors duration-200 ${notify ? 'bg-blue-500' : 'bg-gray-300'}`}
                  >
                    <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${notify ? 'translate-x-[22px]' : 'translate-x-0'}`} />
                  </button>
                </div>

                <div className="pt-2 border-t border-gray-100 space-y-2">
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span className="flex items-center gap-1"><Clock className="w-3 h-3" /> 上次运行</span>
                    <span className="text-green-600 font-medium">全部通过</span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span>运行时长</span>
                    <span>1m 23s</span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span>用例数</span>
                    <span>{cases.length} 个</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

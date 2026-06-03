import { useState, useRef, useEffect } from 'react';
import {
  Search,
  Plus,
  Play,
  Save,
  ChevronRight,
  ChevronDown,
  Folder,
  FolderOpen,
  X,
  MoreHorizontal,
  Pencil,
  Trash2,
  Check,
  GripVertical,
  Layers,
  Tag,
  AlignLeft,
  Clock,
  CheckCircle2,
  Circle,
} from 'lucide-react';

interface ScenarioTreeNode {
  id: string;
  label: string;
  isWorkspace?: boolean;
  count?: number;
  children?: ScenarioTreeNode[];
}

const scenarioTreeData: ScenarioTreeNode[] = [
  {
    id: 'account', label: '开户工作空间', isWorkspace: true, count: 3,
    children: [
      { id: 'account-login', label: '登录场景', count: 1, children: [] },
      { id: 'account-reg', label: '注册流程', count: 2, children: [] },
    ],
  },
  { id: 'trading', label: '交易工作空间', isWorkspace: true, count: 0, children: [] },
];

function ScenarioTreeItem({ node, level = 0 }: { node: ScenarioTreeNode; level?: number }) {
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
        <ChevronRight className={`w-3.5 h-3.5 mr-1 flex-shrink-0 transition-transform text-gray-400 ${expanded ? 'rotate-90' : ''}`} />
        {expanded && hasChildren
          ? <FolderOpen className="w-4 h-4 text-blue-500 mr-2 flex-shrink-0" />
          : <Folder className={`w-4 h-4 mr-2 flex-shrink-0 ${isWorkspace ? 'text-blue-400' : 'text-gray-400'}`} />
        }
        <span className={`flex-1 truncate ${isWorkspace ? 'text-gray-800 font-medium' : 'text-gray-700'}`}>
          {node.label}
        </span>
        {node.count !== undefined && (
          <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5 mr-1 group-hover:opacity-0 transition-opacity">
            {node.count}
          </span>
        )}
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={(e) => e.stopPropagation()}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500 transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
          {!isWorkspace && (
            <div className="relative" ref={menuRef}>
              <button
                onClick={(e) => { e.stopPropagation(); setMenuOpen(!menuOpen); }}
                className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500 transition-colors"
              >
                <MoreHorizontal className="w-3.5 h-3.5" />
              </button>
              {menuOpen && (
                <div className="absolute left-0 top-6 w-32 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1 overflow-hidden">
                  <button
                    onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                  >
                    <Pencil className="w-3.5 h-3.5 text-gray-400" />重命名
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); setMenuOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors"
                  >
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
            ? node.children!.map(child => <ScenarioTreeItem key={child.id} node={child} level={level + 1} />)
            : (
              <div className="py-2 text-center" style={{ paddingLeft: `${level * 14 + 28}px` }}>
                <span className="text-xs text-gray-400">暂无场景</span>
              </div>
            )
          }
        </div>
      )}
    </div>
  );
}

interface ScenarioTab {
  id: string;
  name: string;
}

const methodColor: Record<string, string> = {
  GET: 'text-green-600',
  POST: 'text-blue-600',
  PUT: 'text-orange-500',
  DELETE: 'text-red-500',
  PATCH: 'text-purple-600',
};

const methodBadgeColor: Record<string, string> = {
  GET: 'bg-green-50 text-green-700 border-green-200',
  POST: 'bg-orange-50 text-orange-600 border-orange-200',
  PUT: 'bg-orange-50 text-orange-700 border-orange-200',
  DELETE: 'bg-red-50 text-red-700 border-red-200',
  PATCH: 'bg-purple-50 text-purple-700 border-purple-200',
};

interface Step {
  id: string;
  index: number;
  method: string;
  name: string;
  url: string;
  status: 'success' | 'fail' | 'pending';
}

const mockSteps: Step[] = [
  { id: 's1', index: 1, method: 'POST', name: '用户登录', url: '/api/auth/login', status: 'success' },
  { id: 's2', index: 2, method: 'GET', name: '获取用户信息', url: '/api/user/profile', status: 'success' },
  { id: 's3', index: 3, method: 'POST', name: '创建账户', url: '/api/account/create', status: 'fail' },
  { id: 's4', index: 4, method: 'GET', name: '查询账户状态', url: '/api/account/status', status: 'fail' },
];

const subTabs = [
  { id: 'steps', label: '步骤' },
  { id: 'params', label: '参数' },
  { id: 'assertion', label: '断言' },
  { id: 'history', label: '执行历史' },
  { id: 'settings', label: '设置' },
];

const environments = ['开户-UAT', '开户-PRD', '个人-DEV'];
const modules = ['登录模块', '注册模块', '账户模块', '交易模块'];
const levels = ['P0', 'P1', 'P2', 'P3'];
const statusOptions = ['进行中', '已完成', '已暂停', '待评审'];
const variableSets = ['全局变量集', '开户变量集', '测试变量集'];

function StepRow({ step, onRemove }: { step: Step; onRemove: () => void }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    }
    if (menuOpen) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  return (
    <div className="group flex items-center gap-3 px-4 py-3 border-b border-gray-100 hover:bg-blue-50/30 transition-colors">
      {/* drag handle */}
      <GripVertical className="w-4 h-4 text-gray-300 group-hover:text-gray-400 flex-shrink-0 cursor-grab" />

      {/* step index */}
      <span className="w-5 h-5 rounded-full bg-gray-100 text-gray-500 text-xs flex items-center justify-center flex-shrink-0 font-medium">
        {step.index}
      </span>

      {/* toggle + play + 引用API */}
      <div className="flex items-center gap-1.5 flex-shrink-0">
        <button className="relative w-8 h-4 rounded-full bg-blue-500 flex-shrink-0">
          <span className="absolute top-0.5 right-0.5 w-3 h-3 bg-white rounded-full shadow" />
        </button>
        <button className="w-5 h-5 rounded-full bg-blue-500 flex items-center justify-center flex-shrink-0 hover:bg-blue-600 transition-colors">
          <Play className="w-2.5 h-2.5 text-white fill-white" />
        </button>
        <span className="px-2 py-0.5 bg-blue-50 text-blue-500 border border-blue-300 text-xs rounded font-medium flex-shrink-0">引用 API</span>
      </div>

      {/* method badge */}
      <span className={`px-2 py-0.5 rounded text-xs font-semibold border flex-shrink-0 ${methodBadgeColor[step.method]}`}>
        {step.method}
      </span>

      {/* name */}
      <span className="text-sm text-gray-800 font-medium flex-shrink-0 w-28 truncate">{step.name}</span>

      {/* url */}
      <span className="text-xs text-gray-400 font-mono flex-1 truncate">{step.url}</span>

      {/* status */}
      <div className="flex items-center gap-1.5 flex-shrink-0">
        {step.status === 'success' && (
          <>
            <CheckCircle2 className="w-3.5 h-3.5 text-green-500" />
            <span className="text-xs text-green-600">通过</span>
          </>
        )}
        {step.status === 'fail' && (
          <>
            <Circle className="w-3.5 h-3.5 text-red-400 fill-red-100" />
            <span className="text-xs text-red-500">失败</span>
          </>
        )}
        {step.status === 'pending' && (
          <>
            <Circle className="w-3.5 h-3.5 text-gray-300" />
            <span className="text-xs text-gray-400">待执行</span>
          </>
        )}
      </div>

      {/* actions */}
      <div className="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
        <div className="relative" ref={menuRef}>
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-gray-200 text-gray-400 transition-colors"
          >
            <MoreHorizontal className="w-4 h-4" />
          </button>
          {menuOpen && (
            <div className="absolute right-0 top-8 w-32 bg-white border border-gray-200 rounded-lg shadow-lg z-50 py-1 overflow-hidden">
              <button className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
                <Pencil className="w-3.5 h-3.5 text-gray-400" />编辑
              </button>
              <button
                onClick={() => { setMenuOpen(false); onRemove(); }}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50"
              >
                <Trash2 className="w-3.5 h-3.5" />删除
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// Styled select — matches overall visual language
function FieldSelect({ value, onChange, options }: {
  value: string; onChange: (v: string) => void; options: string[];
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);
  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-gray-300 transition-colors"
      >
        <span className={value ? 'text-gray-800' : 'text-gray-400'}>{value || '请选择'}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-gray-400 transition-transform flex-shrink-0 ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1 overflow-hidden">
          {options.map(opt => (
            <button
              key={opt}
              onClick={() => { onChange(opt); setOpen(false); }}
              className="w-full flex items-center justify-between px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors text-left"
            >
              <span>{opt}</span>
              {value === opt && <Check className="w-3.5 h-3.5 text-blue-500 flex-shrink-0" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function ScenarioPage() {
  const [activeSubTab, setActiveSubTab] = useState('steps');
  const [scenarioTabs, setScenarioTabs] = useState<ScenarioTab[]>([
    { id: 'all', name: '全部场景' },
    { id: '1', name: '开户登录流程' },
  ]);
  const [activeScenarioTab, setActiveScenarioTab] = useState('1');
  const [steps, setSteps] = useState<Step[]>(mockSteps);

  // Right panel fields
  const [scenarioName, setScenarioName] = useState('开户登录流程');
  const [module, setModule] = useState('登录模块');
  const [level, setLevel] = useState('P1');
  const [statusVal, setStatusVal] = useState('进行中');
  const [env, setEnv] = useState('开户-UAT');
  const [varSet, setVarSet] = useState('');
  const [tags, setTags] = useState<string[]>(['登录', '冒烟']);
  const [tagInput, setTagInput] = useState('');
  const [desc, setDesc] = useState('');

  // Env dropdown (top right)
  const [envOpen, setEnvOpen] = useState(false);
  const envRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (envRef.current && !envRef.current.contains(e.target as Node)) setEnvOpen(false);
    }
    if (envOpen) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [envOpen]);

  const addScenarioTab = () => {
    const id = Date.now().toString();
    setScenarioTabs(prev => [...prev, { id, name: '新建场景' }]);
    setActiveScenarioTab(id);
    setSteps([]);
    setScenarioName('');
  };

  const closeScenarioTab = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const remaining = scenarioTabs.filter(t => t.id !== id);
    setScenarioTabs(remaining);
    if (activeScenarioTab === id && remaining.length > 0) {
      setActiveScenarioTab(remaining[remaining.length - 1].id);
    }
  };

  const addStep = () => {
    const newStep: Step = {
      id: Date.now().toString(),
      index: steps.length + 1,
      method: 'GET',
      name: '新建步骤',
      url: '/api/new/step',
      status: 'pending',
    };
    setSteps(prev => [...prev, newStep]);
  };

  const removeStep = (id: string) => {
    setSteps(prev => prev.filter(s => s.id !== id).map((s, i) => ({ ...s, index: i + 1 })));
  };

  const handleTagKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && tagInput.trim()) {
      setTags(prev => [...prev, tagInput.trim()]);
      setTagInput('');
    }
  };

  return (
    <div className="flex-1 flex overflow-hidden min-w-0">

      {/* ── Left sidebar ── */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
        <div className="px-4 pt-4 pb-3">
          <button
            onClick={addScenarioTab}
            className="w-full flex items-center justify-center gap-2 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" /> 新建场景
          </button>
        </div>

        <div className="px-4 pb-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="搜索模块或场景名称"
              className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-3" style={{ scrollbarWidth: 'none' }}>
          <div className="flex items-center justify-between mb-2 px-1 py-1 border-b border-gray-100">
            <span className="text-sm font-semibold text-gray-700">场景目录</span>
            <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5">3</span>
          </div>
          <div className="space-y-0.5">
            {scenarioTreeData.map(node => (
              <ScenarioTreeItem key={node.id} node={node} />
            ))}
          </div>
        </div>
      </aside>

      {/* ── Center + Right ── */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">

        {/* Scenario tab bar */}
        <div className="bg-white border-b border-gray-200 flex-shrink-0 flex items-center min-w-0">
          <style>{`.scenario-tabs::-webkit-scrollbar{display:none}`}</style>
          <div
            className="scenario-tabs flex items-center overflow-x-auto flex-1 min-w-0"
            style={{ scrollbarWidth: 'none' }}
          >
            {scenarioTabs.map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveScenarioTab(tab.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm border-r border-gray-200 flex-shrink-0 transition-colors group ${
                  activeScenarioTab === tab.id
                    ? 'bg-white text-gray-800 border-b-2 border-b-blue-500'
                    : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
                }`}
              >
                <span>{tab.name}</span>
                {tab.id !== 'all' && (
                  <span
                    onClick={(e) => closeScenarioTab(tab.id, e)}
                    className="w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 transition-colors opacity-0 group-hover:opacity-100"
                  >
                    <X className="w-3 h-3" />
                  </span>
                )}
              </button>
            ))}
          </div>
          <div className="flex items-center flex-shrink-0 border-l border-gray-200">
            <button onClick={addScenarioTab} className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
              <Plus className="w-4 h-4" />
            </button>
            <button className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
              <MoreHorizontal className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Content row: steps + right panel */}
        <div className="flex-1 flex overflow-hidden bg-white gap-5">

          {/* Steps area — 与左侧和顶部无缝连接 */}
          <div className="flex-1 flex flex-col overflow-hidden min-w-0 bg-white">

            {/* Sub-tabs */}
            <div className="border-b border-gray-200 px-4 flex-shrink-0">
              <div className="flex">
                {subTabs.map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveSubTab(tab.id)}
                    className={`px-3 py-2.5 text-sm transition-colors border-b-2 ${
                      activeSubTab === tab.id
                        ? 'border-blue-500 text-blue-600 font-medium'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Steps list */}
            {activeSubTab === 'steps' && (
              <div className="flex-1 flex flex-col overflow-hidden">
                {/* toolbar */}
                <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-100 flex-shrink-0 bg-gray-50">
                  <span className="text-sm text-gray-500">共 {steps.length} 个步骤</span>
                  <button
                    onClick={addStep}
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" /> 添加步骤
                  </button>
                </div>

                <div className="flex-1 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
                  {steps.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full gap-3">
                      <div className="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center">
                        <AlignLeft className="w-5 h-5 text-gray-400" />
                      </div>
                      <p className="text-sm text-gray-400">暂无步骤，点击右上角添加</p>
                      <button
                        onClick={addStep}
                        className="flex items-center gap-1.5 px-4 py-2 border border-blue-300 text-blue-600 rounded-lg text-sm hover:bg-blue-50 transition-colors"
                      >
                        <Plus className="w-4 h-4" /> 添加第一个步骤
                      </button>
                    </div>
                  ) : (
                    <div>
                      {steps.map(step => (
                        <StepRow
                          key={step.id}
                          step={step}
                          onRemove={() => removeStep(step.id)}
                        />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {activeSubTab !== 'steps' && (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-sm text-gray-400">
                  {subTabs.find(t => t.id === activeSubTab)?.label} 内容区
                </p>
              </div>
            )}
          </div>

          {/* ── Right property panel — gray bg + white card ── */}
          <div className="w-72 bg-white flex-shrink-0 p-3 flex flex-col">
            <div className="bg-gray-50 rounded-2xl border border-gray-200 shadow-sm flex flex-col flex-1 overflow-hidden">

              {/* Panel header: env + buttons */}
              <div className="px-4 pt-4 pb-3 border-b border-gray-100 flex-shrink-0">
                <div className="relative mb-3" ref={envRef}>
                  <button
                    onClick={() => setEnvOpen(!envOpen)}
                    className="w-full flex items-center gap-1.5 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:border-gray-300 transition-colors bg-white"
                  >
                    <Layers className="w-3.5 h-3.5 text-blue-500 flex-shrink-0" />
                    <span className="flex-1 text-left truncate">{env}</span>
                    <ChevronDown className={`w-3.5 h-3.5 text-gray-400 flex-shrink-0 transition-transform ${envOpen ? 'rotate-180' : ''}`} />
                  </button>
                  {envOpen && (
                    <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1 overflow-hidden">
                      {environments.map(e => (
                        <button key={e} onClick={() => { setEnv(e); setEnvOpen(false); }}
                          className="w-full flex items-center justify-between px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                          <span>{e}</span>
                          {env === e && <Check className="w-3.5 h-3.5 text-blue-500" />}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <button className="flex-1 flex items-center justify-center gap-1.5 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
                    <Play className="w-3.5 h-3.5" /> 执行
                  </button>
                  <button className="flex-1 flex items-center justify-center gap-1.5 py-2 border border-gray-200 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-100 transition-colors">
                    <Save className="w-3.5 h-3.5" /> 保存
                  </button>
                </div>
              </div>

              {/* Form fields */}
              <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5" style={{ scrollbarWidth: 'none' }}>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500 flex items-center gap-1">
                    场景名称 <span className="text-red-400">*</span>
                  </label>
                  <input type="text" value={scenarioName} onChange={e => setScenarioName(e.target.value)}
                    placeholder="请输入场景名称"
                    className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent placeholder:text-gray-400" />
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500">所属模块</label>
                  <FieldSelect value={module} onChange={setModule} options={modules} />
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500">场景等级</label>
                  <FieldSelect value={level} onChange={setLevel} options={levels} />
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500">场景状态</label>
                  <FieldSelect value={statusVal} onChange={setStatusVal} options={statusOptions} />
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500">变量集</label>
                  <FieldSelect value={varSet} onChange={setVarSet} options={variableSets} />
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500 flex items-center gap-1">
                    <Tag className="w-3 h-3" /> 标签
                  </label>
                  <div className="px-3 py-2 bg-white border border-gray-200 rounded-lg min-h-[36px] flex flex-wrap gap-1.5 items-center focus-within:ring-2 focus-within:ring-blue-500 focus-within:border-transparent">
                    {tags.map(tag => (
                      <span key={tag} className="flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-md border border-blue-200">
                        {tag}
                        <button onClick={() => setTags(prev => prev.filter(t => t !== tag))} className="hover:text-blue-900">
                          <X className="w-2.5 h-2.5" />
                        </button>
                      </span>
                    ))}
                    <input type="text" value={tagInput} onChange={e => setTagInput(e.target.value)}
                      onKeyDown={handleTagKeyDown}
                      placeholder={tags.length === 0 ? '输入后回车添加' : ''}
                      className="flex-1 min-w-[60px] text-xs text-gray-700 outline-none placeholder:text-gray-400 bg-transparent" />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-xs text-gray-500 flex items-center gap-1">
                    <AlignLeft className="w-3 h-3" /> 描述
                  </label>
                  <textarea value={desc} onChange={e => setDesc(e.target.value)}
                    placeholder="请对该场景进行描述..." rows={4}
                    className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent placeholder:text-gray-400 resize-none" />
                </div>

                <div className="pt-2 border-t border-gray-100 space-y-2">
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span className="flex items-center gap-1"><Clock className="w-3 h-3" /> 创建时间</span>
                    <span>2026-05-20 10:30</span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span>更新时间</span>
                    <span>2026-05-28 14:22</span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-400">
                    <span>创建人</span>
                    <span>组织管理员</span>
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

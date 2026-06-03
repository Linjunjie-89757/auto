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
  Upload,
  Link,
  FileJson,
  FileText,
  Pencil,
  Trash2,
  Check,
} from 'lucide-react';
import ScenarioPage from './ScenarioPage';
import ExecutePage from './ExecutePage';

interface TreeNode {
  id: string;
  label: string;
  isWorkspace?: boolean;
  children?: TreeNode[];
}

interface RequestTab {
  id: string;
  method: string;
  name: string;
}

const treeData: TreeNode[] = [
  {
    id: 'account', label: '开户工作空间', isWorkspace: true,
    children: [
      { id: 'account-login', label: '登录模块', children: [] },
      { id: 'account-reg', label: '注册模块', children: [] },
    ],
  },
  { id: 'trading', label: '交易工作空间', isWorkspace: true, children: [] },
  { id: 'risk', label: '风控工作空间', isWorkspace: true, children: [] },
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
        <ChevronRight className={`w-3.5 h-3.5 mr-1 flex-shrink-0 transition-transform text-gray-400 ${expanded ? 'rotate-90' : ''}`} />
        {expanded && hasChildren
          ? <FolderOpen className="w-4 h-4 text-blue-500 mr-2 flex-shrink-0" />
          : <Folder className={`w-4 h-4 mr-2 flex-shrink-0 ${isWorkspace ? 'text-blue-400' : 'text-gray-400'}`} />
        }
        <span className={`flex-1 truncate ${isWorkspace ? 'text-gray-800 font-medium' : 'text-gray-700'}`}>
          {node.label}
        </span>

        {/* 操作按钮 — hover 时显示 */}
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity ml-1">
          {/* "+" 新建子模块 */}
          <button
            onClick={(e) => { e.stopPropagation(); }}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-gray-200 text-gray-500 transition-colors"
            title="新建子模块"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>

          {/* "..." 重命名/删除 — 仅子模块 */}
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

const methodColor: Record<string, string> = {
  GET: 'text-green-600',
  POST: 'text-blue-600',
  PUT: 'text-orange-500',
  DELETE: 'text-red-500',
  PATCH: 'text-purple-600',
};

const methodBadgeColor: Record<string, string> = {
  GET: 'bg-green-100 text-green-700 border-green-300',
  POST: 'bg-blue-100 text-blue-700 border-blue-300',
  PUT: 'bg-orange-100 text-orange-700 border-orange-300',
  DELETE: 'bg-red-100 text-red-700 border-red-300',
  PATCH: 'bg-purple-100 text-purple-700 border-purple-300',
};

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

const subTabs = [
  { id: 'headers', label: '请求头' },
  { id: 'body', label: '请求体' },
  { id: 'params', label: 'Params' },
  { id: 'auth', label: 'Auth' },
  { id: 'pre-script', label: '前置处理' },
  { id: 'post-script', label: '后置处理' },
  { id: 'assertion', label: '断言' },
  { id: 'settings', label: '设置' },
  { id: 'usecase', label: '用例' },
];

// fix #4 — body 类型改为轻量 pill 样式
const bodyTypes = ['none', 'form-data', 'x-www-form-urlencoded', 'json', 'xml', 'raw', 'binary'];

const importFormats = [
  { id: 'swagger', label: 'Swagger / OpenAPI', desc: '支持 Swagger 2.0 / OpenAPI 3.x', icon: Link, color: 'text-green-600', bg: 'bg-green-50', border: 'border-green-200', dot: 'bg-green-500' },
  { id: 'postman', label: 'Postman Collection', desc: '支持 Postman v2.0 / v2.1 格式', icon: FileJson, color: 'text-orange-600', bg: 'bg-orange-50', border: 'border-orange-200', dot: 'bg-orange-500' },
  { id: 'har', label: 'HAR 文件', desc: '浏览器导出的 HTTP 存档文件', icon: FileText, color: 'text-purple-600', bg: 'bg-purple-50', border: 'border-purple-200', dot: 'bg-purple-500' },
];

function ImportModal({ onClose }: { onClose: () => void }) {
  const [selectedFormat, setSelectedFormat] = useState('swagger');
  const [importType, setImportType] = useState<'url' | 'file'>('url');
  const fmt = importFormats.find(f => f.id === selectedFormat)!;

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[520px] overflow-hidden" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-5 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Upload className="w-5 h-5 text-blue-500" />
            <span className="text-base font-semibold text-gray-900">导入接口</span>
          </div>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 transition-colors text-gray-400">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div>
            <p className="text-sm font-medium text-gray-700 mb-3">选择导入格式</p>
            <div className="space-y-2">
              {importFormats.map((f) => (
                <button
                  key={f.id}
                  onClick={() => setSelectedFormat(f.id)}
                  className={`w-full flex items-center gap-3 p-3 rounded-xl border-2 transition-all text-left ${
                    selectedFormat === f.id ? `${f.border} ${f.bg}` : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${selectedFormat === f.id ? f.bg : 'bg-gray-100'}`}>
                    <f.icon className={`w-5 h-5 ${selectedFormat === f.id ? f.color : 'text-gray-400'}`} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className={`text-sm font-medium ${selectedFormat === f.id ? 'text-gray-900' : 'text-gray-700'}`}>{f.label}</div>
                    <div className="text-xs text-gray-400 mt-0.5">{f.desc}</div>
                  </div>
                  <div className={`w-4 h-4 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${selectedFormat === f.id ? f.border : 'border-gray-300'}`}>
                    {selectedFormat === f.id && <div className={`w-2 h-2 rounded-full ${f.dot}`} />}
                  </div>
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="text-sm font-medium text-gray-700 mb-3">导入方式</p>
            <div className="flex gap-2 mb-3">
              {(['url', 'file'] as const).map((t) => (
                <button key={t} onClick={() => setImportType(t)}
                  className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors border ${
                    importType === t ? 'bg-blue-50 text-blue-700 border-blue-300' : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {t === 'url' ? 'URL 导入' : '文件上传'}
                </button>
              ))}
            </div>
            {importType === 'url' ? (
              <input type="text"
                placeholder={selectedFormat === 'swagger' ? 'https://api.example.com/v3/api-docs' : '输入文件远程地址'}
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            ) : (
              <label className="flex flex-col items-center justify-center w-full h-28 border-2 border-dashed border-gray-300 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50 transition-colors">
                <Upload className="w-6 h-6 text-gray-400 mb-2" />
                <span className="text-sm text-gray-500">点击或拖拽文件到此处</span>
                <span className="text-xs text-gray-400 mt-1">
                  {selectedFormat === 'swagger' ? '支持 .json / .yaml' : selectedFormat === 'postman' ? '支持 .json' : '支持 .har'}
                </span>
                <input type="file" className="hidden" />
              </label>
            )}
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-200 rounded-lg transition-colors">取消</button>
          <button className="px-5 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">开始导入</button>
        </div>
      </div>
    </div>
  );
}

export default function ApiTestPage() {
  const [activeMainTab, setActiveMainTab] = useState('interface');
  const [activeSubTab, setActiveSubTab] = useState('body');
  const [activeBodyType, setActiveBodyType] = useState('none');
  const [method, setMethod] = useState('GET');
  const [showMethodDropdown, setShowMethodDropdown] = useState(false);
  const methodDropdownRef = useRef<HTMLDivElement>(null);
  const [showImport, setShowImport] = useState(false);

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (methodDropdownRef.current && !methodDropdownRef.current.contains(e.target as Node)) {
        setShowMethodDropdown(false);
      }
    }
    if (showMethodDropdown) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [showMethodDropdown]);
  const [requestTabs, setRequestTabs] = useState<RequestTab[]>([
    { id: '1', method: 'GET', name: '新建请求' },
  ]);
  const [activeRequestTab, setActiveRequestTab] = useState('1');

  const mainTabs = [
    { id: 'interface', label: '接口' },
    { id: 'scene', label: '场景' },
    { id: 'execute', label: '执行' },
    { id: 'report', label: '报告' },
    { id: 'settings', label: '设置' },
  ];

  const addRequestTab = () => {
    const newId = Date.now().toString();
    setRequestTabs([...requestTabs, { id: newId, method: 'GET', name: '新建请求' }]);
    setActiveRequestTab(newId);
  };

  const closeRequestTab = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const remaining = requestTabs.filter((t) => t.id !== id);
    setRequestTabs(remaining);
    if (activeRequestTab === id && remaining.length > 0) {
      setActiveRequestTab(remaining[remaining.length - 1].id);
    }
  };

  return (
    <div className="size-full flex flex-col bg-gray-50">
      {showImport && <ImportModal onClose={() => setShowImport(false)} />}

      {/* 主 Tab — 分段控件 */}
      <div className="bg-white border-b border-gray-200 px-6 flex items-center h-12 flex-shrink-0">
        <div className="inline-flex items-center bg-gray-100 rounded-lg p-1 gap-0.5">
          {mainTabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveMainTab(tab.id)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                activeMainTab === tab.id ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {activeMainTab === 'scene' && <ScenarioPage />}
      {activeMainTab === 'execute' && <ExecutePage />}

      {!['interface', 'scene', 'execute'].includes(activeMainTab) && (
        <div className="flex-1 flex items-center justify-center bg-gray-50">
          <p className="text-sm text-gray-400">
            {['execute', 'report', 'settings'].find(t => t === activeMainTab) && {
              execute: '执行记录', report: '报告中心', settings: '全局设置',
            }[activeMainTab as 'execute' | 'report' | 'settings']} 内容区
          </p>
        </div>
      )}

      <div className={`flex-1 flex overflow-hidden ${activeMainTab !== 'interface' ? 'hidden' : ''}`}>

        {/* 左侧边栏 */}
        <aside className="w-80 bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
          <div className="px-4 pt-4 pb-3 flex gap-2">
            <button onClick={addRequestTab}
              className="flex-1 flex items-center justify-center gap-2 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
            >
              <Plus className="w-4 h-4" /> 新建请求
            </button>
            <button onClick={() => setShowImport(true)}
              className="flex-1 flex items-center justify-center gap-2 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors"
            >
              <Upload className="w-4 h-4" /> 导入
            </button>
          </div>

          <div className="px-4 pb-3">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input type="text" placeholder="搜索模块或请求名称"
                className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>

          {/* 目录树 */}
          <div className="flex-1 overflow-y-auto px-3" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
            <div className="flex items-center justify-between mb-2 px-1 py-1 border-b border-gray-100">
              <span className="text-sm font-semibold text-gray-700">请求目录</span>
              <span className="text-xs text-gray-400 bg-gray-100 rounded px-1.5 py-0.5">0</span>
            </div>
            <div className="space-y-0.5">
              {treeData.map((node) => (
                <TreeItem key={node.id} node={node} />
              ))}
            </div>
          </div>
        </aside>

        {/* Right Panel */}
        <main className="flex-1 flex flex-col overflow-hidden min-w-0">

          {/* Request Tabs Bar — tabs 可滚动，+ 和 ... 固定在右侧 */}
          <div className="bg-white border-b border-gray-200 flex-shrink-0 flex items-center min-w-0">
            <style>{`.tabs-scroll::-webkit-scrollbar{display:none}`}</style>
            {/* 可滚动 tab 列表 */}
            <div
              className="tabs-scroll flex items-center overflow-x-auto flex-1 min-w-0"
              style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
            >
              {requestTabs.map((tab) => (
                <button key={tab.id} onClick={() => setActiveRequestTab(tab.id)}
                  className={`flex items-center gap-2 px-4 py-2.5 text-sm border-r border-gray-200 flex-shrink-0 transition-colors group ${
                    activeRequestTab === tab.id
                      ? 'bg-white text-gray-800 border-b-2 border-b-blue-500'
                      : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
                  }`}
                >
                  <span className={`text-xs font-semibold ${methodColor[tab.method] ?? 'text-gray-500'}`}>{tab.method}</span>
                  <span>{tab.name}</span>
                  <span onClick={(e) => closeRequestTab(tab.id, e)}
                    className="w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 transition-colors opacity-0 group-hover:opacity-100"
                  >
                    <X className="w-3 h-3" />
                  </span>
                </button>
              ))}
            </div>
            {/* 固定在右侧的 + 和 ...，无分隔线 */}
            <div className="flex items-center flex-shrink-0 border-l border-gray-200">
              <button onClick={addRequestTab} className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
                <Plus className="w-4 h-4" />
              </button>
              <button className="w-8 h-9 flex items-center justify-center hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
                <MoreHorizontal className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* URL Row */}
          <div className="bg-white border-b border-gray-200 px-4 py-3 flex items-center gap-2 flex-shrink-0">
            <div className="relative flex-shrink-0" ref={methodDropdownRef}>
              <button
                onClick={() => setShowMethodDropdown(!showMethodDropdown)}
                className={`flex items-center gap-1.5 pl-3 pr-2 py-2 rounded-lg text-sm font-semibold border cursor-pointer transition-colors ${methodBadgeColor[method]}`}
              >
                {method}
                <ChevronDown className={`w-3.5 h-3.5 opacity-70 transition-transform ${showMethodDropdown ? 'rotate-180' : ''}`} />
              </button>
              {showMethodDropdown && (
                <div className="absolute top-full left-0 mt-1 w-36 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1 overflow-hidden">
                  {methods.map((m) => (
                    <button
                      key={m}
                      onClick={() => { setMethod(m); setShowMethodDropdown(false); }}
                      className={`w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors hover:bg-gray-50 ${
                        m === method ? 'font-semibold bg-gray-50' : 'font-medium'
                      } ${methodColor[m] ?? 'text-gray-700'}`}
                    >
                      <span className="flex-1 text-left">{m}</span>
                      {m === method && <Check className="w-3.5 h-3.5 opacity-50 flex-shrink-0" />}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="flex-1 relative">
              <input type="text" placeholder="请输入包含 http/https 的完整 URL 或接口路径"
                className="w-full pl-4 pr-14 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
              <button className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600 transition-colors font-medium">
                Curl
              </button>
            </div>
            <button className="flex items-center gap-2 px-5 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm font-medium transition-colors flex-shrink-0">
              <Play className="w-3.5 h-3.5" /> 发送
            </button>
            <div className="flex items-center border border-gray-300 rounded-lg overflow-hidden flex-shrink-0">
              <button className="flex items-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Save className="w-3.5 h-3.5" /> 保存
              </button>
              <div className="w-px h-5 bg-gray-300" />
              <button className="px-2 py-2 text-gray-500 hover:bg-gray-50 transition-colors">
                <ChevronDown className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* 子 Tab 行 */}
          <div className="bg-white border-b border-gray-200 px-4 flex-shrink-0">
            <div className="flex">
              {subTabs.map((tab) => (
                <button key={tab.id} onClick={() => setActiveSubTab(tab.id)}
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

          {/* Body 类型行 — 独立一行，仅请求体 tab 显示，轻量 pill */}
          {activeSubTab === 'body' && (
            <div className="bg-white border-b border-gray-200 px-4 py-2 flex items-center gap-1 flex-shrink-0">
              {bodyTypes.map((type) => (
                <button key={type} onClick={() => setActiveBodyType(type)}
                  className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                    activeBodyType === type
                      ? 'bg-blue-600 text-white'
                      : 'text-gray-500 hover:bg-gray-100 hover:text-gray-700'
                  }`}
                >
                  {type}
                </button>
              ))}
            </div>
          )}


          {/* Body 内容 + 响应区 */}
          <div className="flex-1 flex flex-col min-h-0">

            {/* 请求内容区 */}
            <div className="flex-1 bg-white flex items-center justify-center min-h-0">
              {activeSubTab === 'body' && activeBodyType === 'none' ? (
                <p className="text-sm text-gray-400">请求没有 Body</p>
              ) : activeSubTab === 'body' ? (
                <textarea placeholder={`输入 ${activeBodyType} 格式内容...`}
                  className="w-full h-full resize-none font-mono text-sm p-4 focus:outline-none"
                />
              ) : (
                <p className="text-sm text-gray-400">{subTabs.find(t => t.id === activeSubTab)?.label} 内容区</p>
              )}
            </div>

            {/* fix #3 — 简洁分割条，不放文字 */}
            <div className="flex-shrink-0 h-2 bg-gray-100 flex items-center justify-center cursor-row-resize">
              <div className="flex gap-0.5">
                <div className="w-4 h-0.5 bg-gray-300 rounded-full" />
                <div className="w-4 h-0.5 bg-gray-300 rounded-full" />
              </div>
            </div>

            {/* fix #3 — 响应区有自己的标题栏 */}
            <div className="flex-1 flex flex-col min-h-0 bg-gray-50">
              <div className="flex-shrink-0 px-4 py-2.5 border-b border-gray-200 bg-white flex items-center justify-between">
                <span className="text-sm font-medium text-gray-700">响应内容</span>
                <span className="text-xs text-gray-400">发送请求后显示结果</span>
              </div>
              <div className="flex-1 flex items-center justify-center min-h-0">
                <p className="text-sm text-gray-400">
                  点击{' '}
                  <span className="text-blue-500 cursor-pointer hover:underline">发送</span>
                  {' '}获取响应内容
                </p>
              </div>
            </div>

          </div>
        </main>
      </div>
    </div>
  );
}

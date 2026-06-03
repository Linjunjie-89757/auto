import { useState, useRef, useEffect } from 'react';
import {
  Check, ChevronDown, Zap, ClipboardCheck,
  RotateCcw, Save, Info, Sparkles,
} from 'lucide-react';

// ── Mock connection pool models ───────────────────────────────────────────

interface AiModel {
  id: string;
  name: string;
  provider: string;
  providerColor: string;
  badge: string;
}

const poolModels: AiModel[] = [
  { id: 'gpt4o', name: 'GPT-4o', provider: 'OpenAI', providerColor: 'bg-green-100 text-green-700', badge: '推荐' },
  { id: 'gpt4t', name: 'GPT-4 Turbo', provider: 'OpenAI', providerColor: 'bg-green-100 text-green-700', badge: '' },
  { id: 'claude35', name: 'Claude 3.5 Sonnet', provider: 'Anthropic', providerColor: 'bg-orange-100 text-orange-700', badge: '推荐' },
  { id: 'claude3o', name: 'Claude 3 Opus', provider: 'Anthropic', providerColor: 'bg-orange-100 text-orange-700', badge: '' },
  { id: 'gemini', name: 'Gemini 1.5 Pro', provider: 'Google', providerColor: 'bg-blue-100 text-blue-700', badge: '' },
  { id: 'deepseek', name: 'DeepSeek V3', provider: 'DeepSeek', providerColor: 'bg-purple-100 text-purple-700', badge: '性价比' },
  { id: 'qwen', name: 'Qwen Max', provider: 'Alibaba', providerColor: 'bg-red-100 text-red-700', badge: '' },
];

// ── Model Selector ────────────────────────────────────────────────────────

function ModelSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const selected = poolModels.find(m => m.id === value);

  useEffect(() => {
    function h(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-2 px-3 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 hover:border-blue-300 transition-colors"
      >
        {selected ? (
          <>
            <span className="flex-1 text-left font-medium text-gray-800">{selected.name}</span>
            <span className={`text-xs px-2 py-0.5 rounded-full ${selected.providerColor}`}>{selected.provider}</span>
          </>
        ) : (
          <span className="flex-1 text-left text-gray-400">请选择模型</span>
        )}
        <ChevronDown className={`w-4 h-4 text-gray-400 transition-transform flex-shrink-0 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 right-0 mt-1.5 bg-white border border-gray-200 rounded-xl shadow-xl z-50 py-1.5 overflow-hidden">
          {poolModels.map(m => (
            <button
              key={m.id}
              onClick={() => { onChange(m.id); setOpen(false); }}
              className={`w-full flex items-center gap-2 px-3 py-2.5 hover:bg-gray-50 transition-colors ${value === m.id ? 'bg-blue-50' : ''}`}
            >
              <div className="flex-1 text-left">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-800">{m.name}</span>
                  {m.badge && (
                    <span className="text-xs px-1.5 py-0.5 bg-blue-50 text-blue-600 border border-blue-200 rounded-full">{m.badge}</span>
                  )}
                </div>
                <span className={`text-xs px-1.5 py-0.5 rounded-full mt-0.5 inline-block ${m.providerColor}`}>{m.provider}</span>
              </div>
              {value === m.id && <Check className="w-4 h-4 text-blue-500 flex-shrink-0" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Toggle ────────────────────────────────────────────────────────────────

function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!value)}
      className={`relative w-10 h-5 rounded-full transition-colors duration-200 ${value ? 'bg-blue-500' : 'bg-gray-300'}`}
    >
      <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${value ? 'translate-x-[22px]' : 'translate-x-0'}`} />
    </button>
  );
}

// ── Tooltip ───────────────────────────────────────────────────────────────

function Tooltip({ text }: { text: string }) {
  const [show, setShow] = useState(false);
  return (
    <span className="relative inline-flex items-center" onMouseEnter={() => setShow(true)} onMouseLeave={() => setShow(false)}>
      <Info className="w-3.5 h-3.5 text-gray-400 hover:text-blue-400 cursor-default transition-colors" />
      {show && (
        <div className="absolute left-5 top-1/2 -translate-y-1/2 z-50 w-52 bg-gray-900 text-white text-xs rounded-xl px-3 py-2.5 shadow-xl leading-relaxed whitespace-pre-line pointer-events-none">
          {text}
          <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900" />
        </div>
      )}
    </span>
  );
}

// ── Temperature Slider ────────────────────────────────────────────────────

function TemperatureSlider({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const label = value <= 0.3 ? '保守' : value <= 0.7 ? '平衡' : '创意';
  const labelColor = value <= 0.3 ? 'text-blue-600' : value <= 0.7 ? 'text-green-600' : 'text-orange-500';
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <label className="text-sm text-gray-600">创意度 (Temperature)</label>
          <Tooltip text={"控制回答的随机性与创意程度。\n· 偏低（精准）：回答更稳定、一致\n· 偏高（创意）：回答更多样、发散\n建议生成任务用 0.5，评审任务用 0.3"} />
        </div>
        <span className={`text-xs font-medium ${labelColor}`}>{label} ({value})</span>
      </div>
      <input
        type="range" min={0} max={1} step={0.1} value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="w-full h-1.5 bg-gray-200 rounded-full appearance-none cursor-pointer accent-blue-500"
      />
      <div className="flex justify-between text-xs text-gray-400">
        <span>精准</span>
        <span>创意</span>
      </div>
    </div>
  );
}

// ── Top-p Slider ──────────────────────────────────────────────────────────

function TopPSlider({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const label = value <= 0.4 ? '聚焦' : value <= 0.7 ? '均衡' : '发散';
  const labelColor = value <= 0.4 ? 'text-blue-600' : value <= 0.7 ? 'text-green-600' : 'text-orange-500';
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <label className="text-sm text-gray-600">采样范围 (Top-p)</label>
          <Tooltip text={"控制 AI 选词的候选范围（核采样）。\n· 偏低（聚焦）：用词更精准、克制\n· 偏高（发散）：表达更丰富、多样\n建议生成任务用 0.9，评审任务用 0.7"} />
        </div>
        <span className={`text-xs font-medium ${labelColor}`}>{label} ({value})</span>
      </div>
      <input
        type="range" min={0.1} max={1} step={0.05} value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="w-full h-1.5 bg-gray-200 rounded-full appearance-none cursor-pointer accent-blue-500"
      />
      <div className="flex justify-between text-xs text-gray-400">
        <span>聚焦</span>
        <span>发散</span>
      </div>
    </div>
  );
}

// ── Role Card ─────────────────────────────────────────────────────────────

interface RoleConfig {
  enabled: boolean;
  model: string;
  prompt: string;
  temperature: number;
  topP: number;
}

const defaultGenPrompt = `你是一名资深测试工程师，擅长接口自动化测试用例的设计。
请根据接口信息生成完整的测试用例，包括正向用例、边界值用例和异常用例。
要求：
1. 用例描述清晰，步骤明确
2. 断言覆盖响应状态码、响应数据结构和业务逻辑
3. 优先考虑高频业务场景`;

const defaultReviewPrompt = `你是一名资深 QA 评审专家，负责对测试用例进行质量评审。
请从以下维度评审用例：
1. 覆盖度：是否覆盖核心业务场景和边界条件
2. 可执行性：步骤是否清晰、断言是否合理
3. 冗余度：是否存在重复或低价值用例
请给出评审结论和改进建议。`;

function RoleCard({
  icon: Icon,
  iconBg,
  iconColor,
  title,
  desc,
  defaultPrompt,
  config,
  onChange,
  onTest,
  testing,
}: {
  icon: React.ElementType;
  iconBg: string;
  iconColor: string;
  title: string;
  desc: string;
  defaultPrompt: string;
  config: RoleConfig;
  onChange: (c: Partial<RoleConfig>) => void;
  onTest: () => void;
  testing: boolean;
}) {
  const [saved, setSaved] = useState(false);

  const handleSave = () => {
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  return (
    <div className={`bg-white rounded-2xl border transition-all duration-200 ${config.enabled ? 'border-blue-200 shadow-md shadow-blue-50' : 'border-gray-200 shadow-sm'}`}>

      {/* Card header */}
      <div className="px-6 pt-5 pb-4 border-b border-gray-100 flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${iconBg}`}>
            <Icon className={`w-5 h-5 ${iconColor}`} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
            <p className="text-xs text-gray-400 mt-0.5">{desc}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0 pt-0.5">
          <span className="text-xs text-gray-500">{config.enabled ? '已启用' : '已禁用'}</span>
          <Toggle value={config.enabled} onChange={v => onChange({ enabled: v })} />
        </div>
      </div>

      {/* Card body */}
      <div className={`px-6 py-5 space-y-5 transition-opacity duration-200 ${config.enabled ? 'opacity-100' : 'opacity-40 pointer-events-none'}`}>

        {/* Model picker */}
        <div className="space-y-2">
          <label className="text-sm font-medium text-gray-700">选择模型</label>
          <p className="text-xs text-gray-400">从 AI 连接池中选择已配置的模型</p>
          <div className="flex items-center gap-2">
            <div className="flex-1">
              <ModelSelect value={config.model} onChange={v => onChange({ model: v })} />
            </div>
            <button
              onClick={onTest}
              disabled={!config.model || testing}
              title="测试模型连接是否正常"
              className="flex items-center gap-1.5 px-3 py-2.5 border border-gray-200 text-gray-600 rounded-xl text-sm hover:bg-gray-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
            >
              <Sparkles className={`w-3.5 h-3.5 ${testing ? 'animate-spin text-blue-500' : ''}`} />
              {testing ? '连接中...' : '测试连接'}
            </button>
          </div>
        </div>

        {/* Temperature */}
        <TemperatureSlider value={config.temperature} onChange={v => onChange({ temperature: v })} />

        {/* Top-p */}
        <TopPSlider value={config.topP} onChange={v => onChange({ topP: v })} />

        {/* Prompt */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-gray-700">角色提示词</label>
            <button
              onClick={() => onChange({ prompt: defaultPrompt })}
              className="flex items-center gap-1 text-xs text-gray-400 hover:text-blue-500 transition-colors"
            >
              <RotateCcw className="w-3 h-3" /> 恢复默认
            </button>
          </div>
          <textarea
            value={config.prompt}
            onChange={e => onChange({ prompt: e.target.value })}
            rows={7}
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none leading-relaxed"
          />
          <p className="text-xs text-gray-400 flex items-center gap-1">
            <Info className="w-3 h-3 flex-shrink-0" />
            提示词会附加在每次 AI 请求前，影响生成结果的风格和质量
          </p>
        </div>

        {/* Save button */}
        <button
          onClick={handleSave}
          className={`w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-medium transition-all ${
            saved ? 'bg-green-500 text-white' : 'bg-blue-600 text-white hover:bg-blue-700'
          }`}
        >
          {saved ? <><Check className="w-4 h-4" /> 已保存</> : <><Save className="w-4 h-4" /> 保存配置</>}
        </button>
      </div>
    </div>
  );
}

// ── Main ──────────────────────────────────────────────────────────────────

export default function AiConfigPage() {
  const [genConfig, setGenConfig] = useState<RoleConfig>({
    enabled: true,
    model: 'gpt4o',
    prompt: defaultGenPrompt,
    temperature: 0.5,
    topP: 0.9,
  });
  const [reviewConfig, setReviewConfig] = useState<RoleConfig>({
    enabled: true,
    model: 'claude35',
    prompt: defaultReviewPrompt,
    temperature: 0.3,
    topP: 0.7,
  });
  const [testingGen, setTestingGen] = useState(false);
  const [testingReview, setTestingReview] = useState(false);

  const handleTest = (role: 'gen' | 'review') => {
    if (role === 'gen') {
      setTestingGen(true);
      setTimeout(() => setTestingGen(false), 2000);
    } else {
      setTestingReview(true);
      setTimeout(() => setTestingReview(false), 2000);
    }
  };

  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-gray-50">

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-8 py-6" style={{ scrollbarWidth: 'thin' }}>

        {/* Info tip */}
        <div className="flex items-center gap-3 mb-6 p-3.5 bg-blue-50 border border-blue-200 rounded-xl">
          <Info className="w-4 h-4 text-blue-500 flex-shrink-0" />
          <span className="text-sm text-blue-700">
            模型来自连接池，共 <strong>{poolModels.length}</strong> 个可选。如需添加，请前往
            <button className="font-medium underline underline-offset-2 ml-1">系统设置 → AI 连接</button>
          </span>
        </div>

        {/* Two role cards */}
        <div className="grid grid-cols-2 gap-6">
          <RoleCard
            icon={Zap}
            iconBg="bg-blue-50"
            iconColor="text-blue-500"
            title="用例生成"
            desc="根据接口信息或需求描述自动生成测试用例"
            defaultPrompt={defaultGenPrompt}
            config={genConfig}
            onChange={patch => setGenConfig(prev => ({ ...prev, ...patch }))}
            onTest={() => handleTest('gen')}
            testing={testingGen}
          />
          <RoleCard
            icon={ClipboardCheck}
            iconBg="bg-green-50"
            iconColor="text-green-500"
            title="用例评审"
            desc="对现有测试用例进行质量评审并给出改进建议"
            defaultPrompt={defaultReviewPrompt}
            config={reviewConfig}
            onChange={patch => setReviewConfig(prev => ({ ...prev, ...patch }))}
            onTest={() => handleTest('review')}
            testing={testingReview}
          />
        </div>
      </div>
    </div>
  );
}

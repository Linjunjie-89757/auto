import { useState, useRef, useEffect, useCallback } from 'react';
import {
  Plus, Trash2, Edit2, Check, X, Eye, EyeOff,
  ChevronRight, Wifi, WifiOff, AlertCircle, Settings,
  Users, Bell, Shield, Palette, Database,
} from 'lucide-react';

// ── Provider brand SVGs (self-contained app icons) ────────────────────────

function OpenAILogo({ size = 28 }: { size?: number }) {
  const blade = "M0-8.5C1.8-8.5 3-7 3-5L3 0C3 1.7 1.7 3 0 3C-1.7 3-3 1.7-3 0L-3-5C-3-7-1.8-8.5 0-8.5Z";
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#000" />
      <g transform="translate(14,14)" fill="white">
        {[0,60,120,180,240,300].map(a => <path key={a} d={blade} transform={`rotate(${a})`} />)}
      </g>
    </svg>
  );
}

function AnthropicLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="#C68642" />
      <path d="M17.3041 3.541h-3.6718l6.696 16.918H24Zm-10.6082 0L0 20.459h3.7442l1.3693-3.5527h7.0052l1.3693 3.5528h3.7442L10.5363 3.5409Zm-.3712 10.2232 2.2914-5.9456 2.2914 5.9456Z" fill="white" />
    </svg>
  );
}

function GoogleLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="white" />
      <path d="M11.04 19.32Q12 21.51 12 24q0-2.49.93-4.68.96-2.19 2.58-3.81t3.81-2.55Q21.51 12 24 12q-2.49 0-4.68-.93a12.3 12.3 0 0 1-3.81-2.58 12.3 12.3 0 0 1-2.58-3.81Q12 2.49 12 0q0 2.49-.96 4.68-.93 2.19-2.55 3.81a12.3 12.3 0 0 1-3.81 2.58Q2.49 12 0 12q2.49 0 4.68.96 2.19.93 3.81 2.55t2.55 3.81" fill="#4285F4" />
    </svg>
  );
}

function DeepSeekLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="#1C3EF0" />
      <path d="M23.748 4.651c-.254-.124-.364.113-.512.233-.051.04-.094.09-.137.137-.372.397-.806.657-1.373.626-.829-.046-1.537.214-2.163.848-.133-.782-.575-1.248-1.247-1.548-.352-.155-.708-.311-.955-.65-.172-.24-.219-.509-.305-.774-.055-.16-.11-.323-.293-.35-.2-.031-.278.136-.356.276-.313.572-.434 1.202-.422 1.84.027 1.436.633 2.58 1.838 3.393.137.094.172.187.129.323-.082.28-.18.553-.266.833-.055.179-.137.218-.328.14a5.5 5.5 0 0 1-1.737-1.179c-.857-.828-1.631-1.743-2.597-2.46a12 12 0 0 0-.689-.47c-.985-.957.13-1.743.387-1.836.27-.098.094-.433-.778-.428-.872.003-1.67.295-2.687.685a3 3 0 0 1-.465.136 9.6 9.6 0 0 0-2.883-.101c-1.885.21-3.39 1.1-4.497 2.622C.082 8.776-.231 10.854.152 13.02c.403 2.284 1.568 4.175 3.36 5.653 1.857 1.533 3.997 2.284 6.438 2.14 1.482-.085 3.132-.284 4.994-1.86.47.234.962.328 1.78.398.629.058 1.235-.031 1.705-.129.735-.155.684-.836.418-.961-2.155-1.004-1.682-.595-2.112-.926 1.095-1.295 2.768-3.598 3.284-6.733.05-.346.115-.834.108-1.114-.004-.171.035-.238.23-.257a4.2 4.2 0 0 0 1.545-.475c1.397-.763 1.96-2.016 2.093-3.517.02-.23-.004-.467-.247-.588M11.58 18.168c-2.088-1.642-3.101-2.183-3.52-2.16-.39.024-.32.472-.234.763.09.288.207.487.371.74.114.167.192.416-.113.603-.673.416-1.842-.14-1.897-.168-1.361-.801-2.5-1.86-3.301-3.306-.775-1.393-1.225-2.888-1.299-4.482-.02-.385.094-.522.477-.592a4.7 4.7 0 0 1 1.53-.038c2.131.311 3.946 1.264 5.467 2.774.868.86 1.525 1.887 2.202 2.89.72 1.066 1.494 2.082 2.48 2.915.348.291.626.513.892.677-.802.09-2.14.109-3.055-.615zm1.001-6.44a.306.306 0 0 1 .415-.287.3.3 0 0 1 .199.288c0 .17-.136.307-.308.307a.303.303 0 0 1-.306-.307m3.11 1.596c-.2.081-.4.151-.591.16a1.25 1.25 0 0 1-.798-.254c-.274-.23-.47-.358-.551-.758a1.7 1.7 0 0 1 .015-.588c.07-.327-.007-.537-.238-.727-.188-.156-.426-.199-.689-.199a.6.6 0 0 1-.254-.078.253.253 0 0 1-.114-.358 1 1 0 0 1 .192-.21c.356-.202.767-.136 1.146.016.352.144.618.408 1.001.782.392.451.462.576.685.915.176.264.336.536.446.848.066.194-.02.353-.25.45" fill="white" />
    </svg>
  );
}

function QwenLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="#FF6A00" />
      <path d="M3.996 4.517h5.291L8.01 6.324 4.153 7.506a1.668 1.668 0 0 0-1.165 1.601v5.786a1.668 1.668 0 0 0 1.165 1.6l3.857 1.183 1.277 1.807H3.996A3.996 3.996 0 0 1 0 15.487V8.513a3.996 3.996 0 0 1 3.996-3.996m16.008 0h-5.291l1.277 1.807 3.857 1.182c.715.227 1.17.889 1.165 1.601v5.786a1.668 1.668 0 0 1-1.165 1.6l-3.857 1.183-1.277 1.807h5.291A3.996 3.996 0 0 0 24 15.487V8.513a3.996 3.996 0 0 0-3.996-3.996m-4.007 8.345H8.002v-1.804h7.995Z" fill="white" />
    </svg>
  );
}

function AzureLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#0078D4"/>
      {/* Azure "A" diamond */}
      <path d="M14 5L8 18.5H13.5L14 17L14.5 18.5H20L14 5Z" fill="white" opacity="0.3"/>
      <path d="M11.5 14L14 6L16.5 14L20 21H16L14 16.5L12 21H8L11.5 14Z" fill="white"/>
    </svg>
  );
}

function OllamaLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#1C1C1E" />
      <ellipse cx="14" cy="13.5" rx="5.5" ry="6.5" fill="#E5E7EB" />
      <circle cx="11.5" cy="12" r="1.3" fill="#1C1C1E" />
      <circle cx="16.5" cy="12" r="1.3" fill="#1C1C1E" />
      <circle cx="11.9" cy="11.7" r="0.5" fill="white" />
      <circle cx="16.9" cy="11.7" r="0.5" fill="white" />
      <ellipse cx="14" cy="16" rx="2" ry="1.2" fill="#D1D5DB" />
      <path d="M9.5 8.5C8.5 5.5 10 4 11.5 5L10.5 8.5Z" fill="#E5E7EB" />
      <path d="M18.5 8.5C19.5 5.5 18 4 16.5 5L17.5 8.5Z" fill="#E5E7EB" />
      <rect x="11" y="19.5" width="6" height="4" rx="1.5" fill="#E5E7EB" />
    </svg>
  );
}

function ZhipuLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#2F54EB"/>
      {/* Crystal/diamond shape */}
      <path d="M14 5L21 11L14 23L7 11L14 5Z" fill="white" opacity="0.25"/>
      <path d="M14 5L21 11L14 16L7 11L14 5Z" fill="white" opacity="0.6"/>
      <path d="M14 16L21 11L14 23L14 16Z" fill="white" opacity="0.4"/>
      <path d="M14 16L7 11L14 23L14 16Z" fill="white" opacity="0.2"/>
    </svg>
  );
}

function KimiLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#111827"/>
      {/* Moon crescent */}
      <path d="M17 8C13 8 9.5 11 9.5 15C9.5 19 13 22 17 22C14 22 11 20.5 9.5 17.5C12 18 15 17 16.5 14.5C18 12 17.5 9 17 8Z" fill="white"/>
      {/* Stars */}
      <circle cx="20" cy="9" r="0.8" fill="white" opacity="0.7"/>
      <circle cx="19" cy="19" r="0.5" fill="white" opacity="0.5"/>
      <circle cx="8" cy="10" r="0.5" fill="white" opacity="0.4"/>
    </svg>
  );
}

function MiniMaxLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="#E8413E" />
      <path d="M11.43 3.92a.86.86 0 1 0-1.718 0v14.236a1.999 1.999 0 0 1-3.997 0V9.022a.86.86 0 1 0-1.718 0v3.87a1.999 1.999 0 0 1-3.997 0V11.49a.57.57 0 0 1 1.139 0v1.404a.86.86 0 0 0 1.719 0V9.022a1.999 1.999 0 0 1 3.997 0v9.134a.86.86 0 0 0 1.719 0V3.92a1.998 1.998 0 1 1 3.996 0v11.788a.57.57 0 1 1-1.139 0zm10.572 3.105a2 2 0 0 0-1.999 1.997v7.63a.86.86 0 0 1-1.718 0V3.923a1.999 1.999 0 0 0-3.997 0v16.16a.86.86 0 0 1-1.719 0V18.08a.57.57 0 1 0-1.138 0v2a1.998 1.998 0 0 0 3.996 0V3.92a.86.86 0 0 1 1.719 0v12.73a1.999 1.999 0 0 0 3.996 0V9.023a.86.86 0 1 1 1.72 0v6.686a.57.57 0 0 0 1.138 0V9.022a2 2 0 0 0-1.998-1.997" fill="white" />
    </svg>
  );
}

function XiaomiLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" fill="white" />
      <path fillRule="evenodd" clipRule="evenodd" d="M12 0C8.016 0 4.756.255 2.493 2.516.23 4.776 0 8.033 0 12.012c0 3.98.23 7.235 2.494 9.497C4.757 23.77 8.017 24 12 24c3.983 0 7.243-.23 9.506-2.491C23.77 19.247 24 15.99 24 12.012c0-3.984-.233-7.243-2.502-9.504C19.234.252 15.978 0 12 0zM4.906 7.405h5.624c1.47 0 3.007.068 3.764.827.746.746.827 2.233.83 3.676v4.54a.15.15 0 0 1-.152.147h-1.947a.15.15 0 0 1-.152-.148V11.83c-.002-.806-.048-1.634-.464-2.051-.358-.36-1.026-.441-1.72-.458H7.158a.15.15 0 0 0-.151.147v6.98a.15.15 0 0 1-.152.148H4.906a.15.15 0 0 1-.15-.148V7.554a.15.15 0 0 1 .15-.149zm12.131 0h1.949a.15.15 0 0 1 .15.15v8.892a.15.15 0 0 1-.15.148h-1.949a.15.15 0 0 1-.151-.148V7.554a.15.15 0 0 1 .151-.149zM8.92 10.948h2.046c.083 0 .15.066.15.147v5.352a.15.15 0 0 1-.15.148H8.92a.15.15 0 0 1-.152-.148v-5.352a.15.15 0 0 1 .152-.147Z" fill="#FF6900" />
    </svg>
  );
}

// ── Provider data ─────────────────────────────────────────────────────────

function CustomLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none">
      <rect width="28" height="28" fill="#F1F5F9" />
      <path d="M14 5C14.4 9.2 18.8 13.6 23 14C18.8 14.4 14.4 18.8 14 23C13.6 18.8 9.2 14.4 5 14C9.2 13.6 13.6 9.2 14 5Z" fill="#64748B" />
    </svg>
  );
}

interface Provider {
  id: string;
  name: string;
  desc: string;
  logo: React.FC<{ size?: number }>;
  color: string;
  bgColor: string;
  borderColor: string;
  textColor: string;
  baseUrl: string;
  models: string[];
  apiKeyLabel: string;
  docsUrl: string;
}

const providers: Provider[] = [
  {
    id: 'openai',
    name: 'OpenAI',
    desc: 'GPT-4o、GPT-4 Turbo 等系列模型',
    logo: OpenAILogo,
    color: '#10a37f',
    bgColor: 'bg-[#10a37f]/10',
    borderColor: 'border-[#10a37f]/30',
    textColor: 'text-[#10a37f]',
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://platform.openai.com',
  },
  {
    id: 'anthropic',
    name: 'Anthropic',
    desc: 'Claude 3.5 Sonnet、Claude 3 Opus 等',
    logo: AnthropicLogo,
    color: '#cc785c',
    bgColor: 'bg-[#cc785c]/10',
    borderColor: 'border-[#cc785c]/30',
    textColor: 'text-[#cc785c]',
    baseUrl: 'https://api.anthropic.com',
    models: ['claude-3-5-sonnet-20241022', 'claude-3-opus-20240229', 'claude-3-haiku-20240307'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://console.anthropic.com',
  },
  {
    id: 'google',
    name: 'Google Gemini',
    desc: 'Gemini 1.5 Pro、Gemini 1.5 Flash 等',
    logo: GoogleLogo,
    color: '#4285F4',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-200',
    textColor: 'text-blue-600',
    baseUrl: 'https://generativelanguage.googleapis.com/v1',
    models: ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-1.0-pro'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://ai.google.dev',
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    desc: 'DeepSeek V3、DeepSeek Coder 等',
    logo: DeepSeekLogo,
    color: '#4f46e5',
    bgColor: 'bg-indigo-50',
    borderColor: 'border-indigo-200',
    textColor: 'text-indigo-600',
    baseUrl: 'https://api.deepseek.com/v1',
    models: ['deepseek-chat', 'deepseek-coder', 'deepseek-reasoner'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://platform.deepseek.com',
  },
  {
    id: 'qwen',
    name: '阿里云 / Qwen',
    desc: 'Qwen-Max、Qwen-Plus、Qwen-Turbo 等',
    logo: QwenLogo,
    color: '#ff6a00',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-200',
    textColor: 'text-orange-600',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long'],
    apiKeyLabel: 'DashScope API Key',
    docsUrl: 'https://dashscope.aliyun.com',
  },
  {
    id: 'azure',
    name: 'Azure OpenAI',
    desc: '微软 Azure 托管的 OpenAI 模型',
    logo: AzureLogo,
    color: '#0078d4',
    bgColor: 'bg-sky-50',
    borderColor: 'border-sky-200',
    textColor: 'text-sky-600',
    baseUrl: 'https://{resource}.openai.azure.com',
    models: ['gpt-4o', 'gpt-4-turbo', 'gpt-35-turbo'],
    apiKeyLabel: 'Azure API Key',
    docsUrl: 'https://portal.azure.com',
  },
  {
    id: 'xiaomi',
    name: '小米 / MiMo',
    desc: 'MiMo 推理模型，小米开源系列',
    logo: XiaomiLogo,
    color: '#ff6900',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-200',
    textColor: 'text-orange-600',
    baseUrl: 'https://api.mimo.xiaomi.com/v1',
    models: ['mimo-7b', 'mimo-7b-rl'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://github.com/XiaoMi/MiMo',
  },
  {
    id: 'zhipu',
    name: '智谱 AI',
    desc: 'GLM-4、GLM-4-Flash 等系列模型',
    logo: ZhipuLogo,
    color: '#3b6ef7',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-200',
    textColor: 'text-blue-600',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    models: ['glm-4', 'glm-4-flash', 'glm-4-air', 'glm-3-turbo'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://open.bigmodel.cn',
  },
  {
    id: 'kimi',
    name: 'Kimi',
    desc: 'Moonshot AI，擅长长文本理解',
    logo: KimiLogo,
    color: '#1a73e8',
    bgColor: 'bg-indigo-50',
    borderColor: 'border-indigo-200',
    textColor: 'text-indigo-600',
    baseUrl: 'https://api.moonshot.cn/v1',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://platform.moonshot.cn',
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    desc: 'MiniMax-Text、abab 系列模型',
    logo: MiniMaxLogo,
    color: '#7c3aed',
    bgColor: 'bg-violet-50',
    borderColor: 'border-violet-200',
    textColor: 'text-violet-600',
    baseUrl: 'https://api.minimax.chat/v1',
    models: ['abab6.5s-chat', 'abab6.5-chat', 'abab5.5-chat'],
    apiKeyLabel: 'API Key',
    docsUrl: 'https://www.minimaxi.com',
  },
  {
    id: 'ollama',
    name: 'Ollama',
    desc: '本地运行的开源大模型',
    logo: OllamaLogo,
    color: '#374151',
    bgColor: 'bg-gray-100',
    borderColor: 'border-gray-300',
    textColor: 'text-gray-700',
    baseUrl: 'http://localhost:11434/v1',
    models: ['llama3', 'mistral', 'codellama', 'qwen2'],
    apiKeyLabel: 'API Key（本地可留空）',
    docsUrl: 'https://ollama.com',
  },
  {
    id: 'custom',
    name: '自定义',
    desc: '支持所有兼容 OpenAI API 规范的模型提供商',
    logo: CustomLogo,
    color: '#64748B',
    bgColor: 'bg-slate-50',
    borderColor: 'border-slate-200',
    textColor: 'text-slate-600',
    baseUrl: 'https://your-api-endpoint/v1',
    models: [],
    apiKeyLabel: 'API Key',
    docsUrl: '',
  },
];

// ── Connection types ───────────────────────────────────────────────────────

interface Connection {
  id: string;
  name: string;
  providerId: string;
  model: string;
  apiKey: string;
  baseUrl: string;
  status: 'connected' | 'error' | 'untested';
  createdAt: string;
}

const initConnections: Connection[] = [
  { id: 'c1', name: 'OpenAI 生产环境', providerId: 'openai', model: 'gpt-4o', apiKey: 'sk-••••••••••••••••••••••••••••••••', baseUrl: 'https://api.openai.com/v1', status: 'connected', createdAt: '2026-05-20' },
  { id: 'c2', name: 'Claude 评审专用', providerId: 'anthropic', model: 'claude-3-5-sonnet-20241022', apiKey: 'sk-ant-••••••••••••••••••••••••••••', baseUrl: 'https://api.anthropic.com', status: 'connected', createdAt: '2026-05-22' },
  { id: 'c3', name: 'DeepSeek 性价比', providerId: 'deepseek', model: 'deepseek-chat', apiKey: 'sk-••••••••••••••••••••••••••••••••', baseUrl: 'https://api.deepseek.com/v1', status: 'error', createdAt: '2026-05-28' },
];

// ── Settings nav ───────────────────────────────────────────────────────────

const settingsNav = [
  { id: 'ai', label: 'AI 连接', icon: Database, desc: '配置 AI 大模型连接池' },
  { id: 'general', label: '通用设置', icon: Settings, desc: '平台基础配置' },
  { id: 'team', label: '团队管理', icon: Users, desc: '成员与权限管理' },
  { id: 'notify', label: '通知设置', icon: Bell, desc: '消息推送与告警' },
  { id: 'security', label: '安全设置', icon: Shield, desc: '密钥与访问控制' },
  { id: 'theme', label: '外观设置', icon: Palette, desc: '主题与显示偏好' },
];

// ── Add/Edit Connection Modal ─────────────────────────────────────────────

function ConnectionModal({
  initial,
  onClose,
  onSave,
}: {
  initial?: Connection;
  onClose: () => void;
  onSave: (c: Connection) => void;
}) {
  const [step, setStep] = useState<'provider' | 'config'>(initial ? 'config' : 'provider');
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(
    initial ? providers.find(p => p.id === initial.providerId) ?? null : null
  );
  const [name, setName] = useState(initial?.name ?? '');
  const [model, setModel] = useState(initial?.model ?? '');
  const [apiKey, setApiKey] = useState(initial ? '' : '');
  const [baseUrl, setBaseUrl] = useState(initial?.baseUrl ?? '');
  const [showKey, setShowKey] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<'ok' | 'fail' | null>(null);
  const [fetchedModels, setFetchedModels] = useState<string[]>([]);
  const [fetchingModels, setFetchingModels] = useState(false);
  const [fetchModelError, setFetchModelError] = useState<string | null>(null);
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const modelDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function h(e: MouseEvent) {
      if (modelDropdownRef.current && !modelDropdownRef.current.contains(e.target as Node)) {
        setShowModelDropdown(false);
      }
    }
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const handleFetchModels = async () => {
    if (!baseUrl || !apiKey) {
      setFetchModelError('请先填写 API Key 和 API Url');
      return;
    }
    setFetchingModels(true);
    setFetchModelError(null);
    setFetchedModels([]);
    try {
      const res = await fetch(`${baseUrl.replace(/\/$/, '')}/models`, {
        headers: { Authorization: `Bearer ${apiKey}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      const ids: string[] = (json.data ?? json.models ?? []).map((m: any) => m.id ?? m.name ?? m).filter(Boolean);
      if (ids.length === 0) throw new Error('未获取到模型');
      setFetchedModels(ids);
      setShowModelDropdown(true);
    } catch {
      const fallback = selectedProvider?.models ?? [];
      if (fallback.length > 0) {
        setFetchedModels(fallback);
        setShowModelDropdown(true);
      } else {
        setFetchModelError('获取失败，请检查 API Key 和 API Url 是否正确');
      }
    } finally {
      setFetchingModels(false);
    }
  };

  const handleSelectProvider = (p: Provider) => {
    setSelectedProvider(p);
    setBaseUrl(p.baseUrl);
    setModel(p.models[0]);
    setName(`${p.name} 连接`);
    setStep('config');
  };

  const handleTest = () => {
    setTesting(true);
    setTestResult(null);
    setTimeout(() => {
      setTesting(false);
      setTestResult(Math.random() > 0.3 ? 'ok' : 'fail');
    }, 1800);
  };

  const handleSave = () => {
    if (!selectedProvider) return;
    onSave({
      id: initial?.id ?? `c${Date.now()}`,
      name: name || `${selectedProvider.name} 连接`,
      providerId: selectedProvider.id,
      model,
      apiKey: apiKey || initial?.apiKey || '(未填写)',
      baseUrl,
      status: testResult === 'ok' ? 'connected' : 'untested',
      createdAt: initial?.createdAt ?? new Date().toISOString().slice(0, 10),
    });
  };

  return (
    <div className="fixed inset-0 bg-black/30 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden">

        {/* Modal header */}
        <div className="px-6 pt-5 pb-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <div>
            <h2 className="text-base font-semibold text-gray-900">
              {initial ? '编辑连接' : step === 'provider' ? '选择供应商' : '配置连接'}
            </h2>
            {!initial && step === 'config' && selectedProvider && (
              <button onClick={() => setStep('provider')} className="text-xs text-blue-500 hover:text-blue-600 mt-0.5 flex items-center gap-1">
                ← 重新选择供应商
              </button>
            )}
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors">
            <X className="w-4 h-4 text-gray-500" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto [&::-webkit-scrollbar]:hidden" style={{ scrollbarWidth: 'none' }}>
          {/* Step 1: Provider selection */}
          {step === 'provider' && (
            <div className="p-6">
              <p className="text-sm text-gray-500 mb-5">选择要接入的 AI 服务供应商</p>
              <div className="grid grid-cols-2 gap-3">
                {providers.map(p => (
                  <button
                    key={p.id}
                    onClick={() => handleSelectProvider(p)}
                    className={`flex items-center gap-4 p-4 rounded-xl border-2 text-left transition-all hover:shadow-md hover:scale-[1.01] ${p.bgColor} ${p.borderColor}`}
                  >
                    <div className="w-12 h-12 rounded-xl flex-shrink-0 overflow-hidden shadow-sm">
                      <p.logo size={48} />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-semibold text-gray-900">{p.name}</div>
                      <div className="text-xs text-gray-500 mt-0.5 line-clamp-2">{p.desc}</div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-gray-400 flex-shrink-0 ml-auto" />
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Step 2: Config form */}
          {step === 'config' && selectedProvider && (
            <div className="p-6 space-y-5">
              {/* Provider badge */}
              <div className={`flex items-center gap-3 p-3 rounded-xl ${selectedProvider.bgColor} ${selectedProvider.borderColor} border`}>
                <div className="w-10 h-10 rounded-xl flex-shrink-0 overflow-hidden shadow-sm">
                  <selectedProvider.logo size={40} />
                </div>
                <div>
                  <div className="text-sm font-semibold text-gray-900">{selectedProvider.name}</div>
                  <div className="text-xs text-gray-500">{selectedProvider.desc}</div>
                </div>
              </div>

              {/* Connection name */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-gray-700">连接名称</label>
                <input
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder={`${selectedProvider.name} 连接`}
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* API Url */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-sm font-medium text-gray-700">API Url</label>
                  <button
                    onClick={() => setBaseUrl(selectedProvider.baseUrl)}
                    className="text-xs text-blue-500 hover:text-blue-600"
                  >
                    恢复默认
                  </button>
                </div>
                <input
                  value={baseUrl}
                  onChange={e => setBaseUrl(e.target.value)}
                  placeholder={selectedProvider.baseUrl}
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
                />
                <p className="text-xs text-gray-400">支持自定义代理地址或私有部署地址</p>
              </div>

              {/* API Key */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-gray-700">{selectedProvider.apiKeyLabel}</label>
                <div className="relative">
                  <input
                    type={showKey ? 'text' : 'password'}
                    value={apiKey}
                    onChange={e => setApiKey(e.target.value)}
                    placeholder={initial ? '不修改请留空' : '请输入 API Key'}
                    className="w-full px-3 py-2.5 pr-10 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
                  />
                  <button
                    type="button"
                    onClick={() => setShowKey(!showKey)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Model */}
              <div className="space-y-1.5" ref={modelDropdownRef}>
                <div className="flex items-center justify-between">
                  <label className="text-sm font-medium text-gray-700">模型名称</label>
                  <button
                    type="button"
                    onClick={handleFetchModels}
                    disabled={fetchingModels}
                    className="flex items-center gap-1.5 text-xs text-gray-500 hover:text-blue-500 border border-gray-200 hover:border-blue-300 px-2.5 py-1 rounded-lg transition-colors disabled:opacity-50"
                  >
                    {fetchingModels ? (
                      <svg className="w-3 h-3 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="32" strokeDashoffset="12" strokeLinecap="round"/>
                      </svg>
                    ) : (
                      <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                      </svg>
                    )}
                    {fetchingModels ? '获取中...' : '获取模型列表'}
                  </button>
                </div>

                <div className="relative">
                  <input
                    value={model}
                    onChange={e => setModel(e.target.value)}
                    placeholder="例如：gpt-4o、deepseek-chat、qwen-max"
                    className={`w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono ${fetchedModels.length > 0 ? 'pr-10' : ''}`}
                  />
                  {fetchedModels.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setShowModelDropdown(v => !v)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 w-6 h-6 rounded-md bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
                    >
                      <ChevronRight className={`w-3.5 h-3.5 text-gray-500 rotate-90 transition-transform ${showModelDropdown ? 'rotate-[-90deg]' : 'rotate-90'}`} />
                    </button>
                  )}

                  {showModelDropdown && fetchedModels.length > 0 && (
                    <div className="absolute top-full left-0 right-0 mt-1.5 bg-white border border-gray-200 rounded-xl shadow-xl z-50 overflow-hidden">
                      <div className="px-3 py-2 border-b border-gray-100 flex items-center justify-between">
                        <span className="text-xs text-gray-400">共 {fetchedModels.length} 个模型</span>
                        <span className="text-xs text-blue-500">点击选择</span>
                      </div>
                      <div className="max-h-48 overflow-y-auto [&::-webkit-scrollbar]:hidden" style={{ scrollbarWidth: 'none' }}>
                        {fetchedModels.map(m => (
                          <button
                            key={m}
                            type="button"
                            onClick={() => { setModel(m); setShowModelDropdown(false); }}
                            className={`w-full text-left px-3 py-2.5 text-sm font-mono hover:bg-blue-50 hover:text-blue-700 transition-colors flex items-center justify-between ${model === m ? 'bg-blue-50 text-blue-700' : 'text-gray-700'}`}
                          >
                            <span className="truncate">{m}</span>
                            {model === m && <Check className="w-3.5 h-3.5 flex-shrink-0 ml-2" />}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {fetchModelError && (
                  <p className="text-xs text-red-500 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 flex-shrink-0" />{fetchModelError}
                  </p>
                )}
              </div>

              {/* Test result */}
              {testResult && (
                <div className={`flex items-center gap-2 p-3 rounded-xl text-sm ${testResult === 'ok' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
                  {testResult === 'ok' ? <Check className="w-4 h-4 flex-shrink-0" /> : <AlertCircle className="w-4 h-4 flex-shrink-0" />}
                  {testResult === 'ok' ? '连接测试成功，模型响应正常' : '连接失败，请检查 API Key 或网络设置'}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Modal footer */}
        {step === 'config' && selectedProvider && (
          <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between flex-shrink-0">
            <button
              onClick={handleTest}
              disabled={testing}
              className="flex items-center gap-2 px-4 py-2 border border-gray-200 text-gray-600 rounded-xl text-sm hover:bg-gray-50 transition-colors disabled:opacity-50"
            >
              <Wifi className={`w-4 h-4 ${testing ? 'animate-pulse text-blue-500' : ''}`} />
              {testing ? '测试中...' : '测试连接'}
            </button>
            <div className="flex items-center gap-2">
              <button onClick={onClose} className="px-4 py-2 border border-gray-200 text-gray-600 rounded-xl text-sm hover:bg-gray-50 transition-colors">
                取消
              </button>
              <button
                onClick={handleSave}
                className="px-5 py-2 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors"
              >
                {initial ? '保存修改' : '添加连接'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Connection Card ───────────────────────────────────────────────────────

function ConnectionCard({
  conn,
  onEdit,
  onDelete,
  onTest,
  testing,
}: {
  conn: Connection;
  onEdit: () => void;
  onDelete: () => void;
  onTest: () => void;
  testing: boolean;
}) {
  const provider = providers.find(p => p.id === conn.providerId);
  if (!provider) return null;

  const statusMap = {
    connected: { icon: Wifi, color: 'text-green-500', bg: 'bg-green-50 border-green-200', label: '已连接' },
    error: { icon: WifiOff, color: 'text-red-500', bg: 'bg-red-50 border-red-200', label: '连接异常' },
    untested: { icon: AlertCircle, color: 'text-gray-400', bg: 'bg-gray-50 border-gray-200', label: '未测试' },
  };
  const s = statusMap[conn.status];
  const StatusIcon = s.icon;

  return (
    <div className="bg-white border border-gray-200 rounded-2xl p-5 hover:shadow-md transition-all duration-200 group">
      <div className="flex items-start gap-4">
        {/* Provider logo */}
        <div className="w-12 h-12 rounded-xl flex-shrink-0 overflow-hidden shadow-sm">
          <provider.logo size={48} />
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-gray-900">{conn.name}</span>
            <span className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full border ${s.bg} ${s.color}`}>
              <StatusIcon className="w-3 h-3" />
              {s.label}
            </span>
          </div>
          <div className="flex items-center gap-3 mt-1.5 flex-wrap">
            <span className={`text-xs px-2 py-0.5 rounded-full ${provider.bgColor} ${provider.textColor}`}>{provider.name}</span>
            <span className="text-xs text-gray-500 font-mono bg-gray-50 px-2 py-0.5 rounded-lg">{conn.model}</span>
          </div>
          <div className="text-xs text-gray-400 mt-1.5 font-mono truncate">{conn.baseUrl}</div>
          <div className="text-xs text-gray-400 mt-0.5">添加于 {conn.createdAt}</div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1.5 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={onTest}
            disabled={testing}
            title="测试连接"
            className="p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded-lg transition-colors disabled:opacity-50"
          >
            <Wifi className={`w-4 h-4 ${testing ? 'animate-pulse text-blue-500' : ''}`} />
          </button>
          <button onClick={onEdit} title="编辑" className="p-1.5 text-gray-400 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">
            <Edit2 className="w-4 h-4" />
          </button>
          <button onClick={onDelete} title="删除" className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors">
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

// ── AI Connection Page ────────────────────────────────────────────────────

function AiConnectionPage() {
  const [connections, setConnections] = useState<Connection[]>(initConnections);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingConn, setEditingConn] = useState<Connection | undefined>();
  const [testingId, setTestingId] = useState<string | null>(null);

  const handleSave = (c: Connection) => {
    setConnections(prev => {
      const idx = prev.findIndex(x => x.id === c.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = c;
        return next;
      }
      return [...prev, c];
    });
    setModalOpen(false);
    setEditingConn(undefined);
  };

  const handleDelete = (id: string) => {
    setConnections(prev => prev.filter(c => c.id !== id));
  };

  const handleTest = (id: string) => {
    setTestingId(id);
    setTimeout(() => {
      setTestingId(null);
      setConnections(prev => prev.map(c =>
        c.id === id ? { ...c, status: Math.random() > 0.2 ? 'connected' : 'error' } : c
      ));
    }, 2000);
  };

  const connectedCount = connections.filter(c => c.status === 'connected').length;

  return (
    <div className="flex-1 overflow-y-auto px-8 py-7 [&::-webkit-scrollbar]:hidden" style={{ scrollbarWidth: 'none' }}>

      {/* Page header */}
      <div className="flex items-start justify-between mb-7">
        <div>
          <h2 className="text-base font-semibold text-gray-900">AI 连接池</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            管理接入的 AI 大模型服务，连接池中的模型可在 <span className="text-blue-500">用例中心 → AI 配置</span> 中使用
          </p>
        </div>
        <button
          onClick={() => { setEditingConn(undefined); setModalOpen(true); }}
          className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 transition-colors flex-shrink-0"
        >
          <Plus className="w-4 h-4" />
          添加连接
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4 mb-7">
        {[
          { label: '连接总数', value: connections.length, color: 'text-blue-600', bg: 'bg-blue-50' },
          { label: '正常连接', value: connectedCount, color: 'text-green-600', bg: 'bg-green-50' },
          { label: '异常连接', value: connections.filter(c => c.status === 'error').length, color: 'text-red-500', bg: 'bg-red-50' },
          { label: '可用供应商', value: providers.length, color: 'text-purple-600', bg: 'bg-purple-50' },
        ].map(s => (
          <div key={s.label} className="bg-white border border-gray-200 rounded-2xl px-5 py-4">
            <div className="text-xs text-gray-500 mb-1">{s.label}</div>
            <div className={`text-2xl font-bold ${s.color}`}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* Connection list */}
      {connections.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="w-16 h-16 bg-gray-100 rounded-2xl flex items-center justify-center mb-4">
            <Database className="w-8 h-8 text-gray-400" />
          </div>
          <p className="text-sm font-medium text-gray-600 mb-1">暂无连接配置</p>
          <p className="text-xs text-gray-400 mb-4">点击右上角「添加连接」开始配置 AI 模型</p>
          <button
            onClick={() => setModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-xl text-sm hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" /> 添加第一个连接
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {connections.map(conn => (
            <ConnectionCard
              key={conn.id}
              conn={conn}
              onEdit={() => { setEditingConn(conn); setModalOpen(true); }}
              onDelete={() => handleDelete(conn.id)}
              onTest={() => handleTest(conn.id)}
              testing={testingId === conn.id}
            />
          ))}
        </div>
      )}

      {/* Supported providers */}
      <div className="mt-8">
        <h3 className="text-sm font-medium text-gray-700 mb-4">支持的供应商</h3>
        <div className="grid grid-cols-6 gap-3">
          {providers.map(p => {
            const hasConn = connections.some(c => c.providerId === p.id);
            return (
              <div
                key={p.id}
                className={`flex flex-col items-center gap-2 p-3 rounded-xl border transition-all ${hasConn ? `${p.bgColor} ${p.borderColor}` : 'bg-gray-50 border-gray-200'}`}
              >
                <div className={`w-10 h-10 rounded-xl overflow-hidden shadow-sm transition-opacity ${hasConn ? 'opacity-100' : 'opacity-35'}`}>
                  <p.logo size={40} />
                </div>
                <span className={`text-xs text-center leading-tight ${hasConn ? 'text-gray-700 font-medium' : 'text-gray-400'}`}>
                  {p.name.split(' ')[0]}
                </span>
                {hasConn && <div className="w-1.5 h-1.5 rounded-full bg-green-500" />}
              </div>
            );
          })}
        </div>
      </div>

      {/* Modal */}
      {modalOpen && (
        <ConnectionModal
          initial={editingConn}
          onClose={() => { setModalOpen(false); setEditingConn(undefined); }}
          onSave={handleSave}
        />
      )}
    </div>
  );
}

// ── Placeholder for other settings pages ─────────────────────────────────

function PlaceholderPage({ label }: { label: string }) {
  return (
    <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
      {label} 页面建设中...
    </div>
  );
}

// ── Main Settings Page ────────────────────────────────────────────────────

export default function SettingsPage() {
  const [activeNav, setActiveNav] = useState('ai');

  return (
    <div className="flex-1 flex overflow-hidden bg-gray-50">

      {/* Left settings nav */}
      <aside className="w-56 bg-white border-r border-gray-200 flex-shrink-0 overflow-y-auto py-4 px-3">
        <p className="text-xs text-gray-400 font-medium uppercase tracking-wider px-3 mb-2">设置分类</p>
        {settingsNav.map(item => (
          <button
            key={item.id}
            onClick={() => setActiveNav(item.id)}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-colors mb-0.5 ${
              activeNav === item.id
                ? 'bg-blue-50 text-blue-700'
                : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
            }`}
          >
            <item.icon className={`w-4 h-4 flex-shrink-0 ${activeNav === item.id ? 'text-blue-500' : 'text-gray-400'}`} />
            <div className="min-w-0">
              <div className="text-sm font-medium truncate">{item.label}</div>
              <div className="text-xs text-gray-400 truncate">{item.desc}</div>
            </div>
          </button>
        ))}
      </aside>

      {/* Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {activeNav === 'ai' ? (
          <AiConnectionPage />
        ) : (
          <PlaceholderPage label={settingsNav.find(n => n.id === activeNav)?.label ?? ''} />
        )}
      </div>
    </div>
  );
}

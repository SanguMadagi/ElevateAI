import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { agentService } from '../services/api';
import { 
  Brain, Plus, MessageSquare, Trash2, Send, ChevronLeft, 
  Menu, X, Sparkles, AlertCircle, RefreshCw, Bot, User, 
  ArrowLeft, ShieldCheck, CheckCircle2, Edit3, Check
} from 'lucide-react';
import toast from 'react-hot-toast';

const SUGGESTION_PROMPTS = [
  {
    title: "Weakest Skills",
    prompt: "What are my weakest skills based on my assessment results?",
    desc: "Examine your latest evaluations and weak concept detections."
  },
  {
    title: "Repeated Mistakes",
    prompt: "What mistakes am I repeatedly making across my technical evaluations?",
    desc: "Pinpoint recurring anti-patterns and performance gaps."
  },
  {
    title: "Mock Interview Review",
    prompt: "How did I perform in my mock interviews and what should I improve?",
    desc: "Summarize mock interview turn scores and architectural feedback."
  },
  {
    title: "Resume & Skill Match",
    prompt: "How well does my resume match my demonstrated skills and target role?",
    desc: "Compare resume project claims against evaluated competency."
  }
];

const renderInline = (text) => {
  const tokens = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).filter(Boolean);
  return tokens.map((token, index) => {
    if (token.startsWith('**') && token.endsWith('**')) {
      return <strong key={index} className="text-white font-bold">{token.slice(2, -2)}</strong>;
    }
    if (token.startsWith('`') && token.endsWith('`')) {
      return <code key={index} className="bg-slate-950 px-1.5 py-0.5 rounded text-violet-300 font-mono text-xs border border-slate-800">{token.slice(1, -1)}</code>;
    }
    return <React.Fragment key={index}>{token}</React.Fragment>;
  });
};

const FormattedMessage = ({ content }) => {
  if (!content) return null;
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const blocks = [];
  let list = null;
  let code = null;

  const flushList = () => {
    if (list) blocks.push(list);
    list = null;
  };
  const flushCode = () => {
    if (code) blocks.push(code);
    code = null;
  };

  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('```')) {
      if (code) flushCode();
      else code = { type: 'code', language: trimmed.slice(3).trim(), lines: [] };
      return;
    }
    if (code) {
      code.lines.push(line);
      return;
    }
    if (!trimmed) {
      flushList();
      return;
    }
    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      flushList();
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
      return;
    }
    const bullet = trimmed.match(/^[-*]\s+(.+)$/);
    const numbered = trimmed.match(/^\d+[.)]\s+(.+)$/);
    if (bullet || numbered) {
      const type = bullet ? 'ul' : 'ol';
      if (!list || list.type !== type) {
        flushList();
        list = { type, items: [] };
      }
      list.items.push(bullet ? bullet[1] : numbered[1]);
      return;
    }
    flushList();
    blocks.push({ type: 'paragraph', text: trimmed, key: index });
  });
  flushList();
  flushCode();

  return (
    <div className="space-y-3 leading-relaxed text-sm md:text-base">
      {blocks.map((block, index) => {
        if (block.type === 'code') {
          return <div key={index} className="rounded-2xl overflow-hidden border border-slate-800 bg-slate-950 font-mono text-xs md:text-sm">
            {block.language && <div className="bg-slate-900 px-4 py-1.5 text-xs text-slate-400 font-semibold border-b border-slate-800">{block.language}</div>}
            <pre className="p-4 text-emerald-400 overflow-x-auto whitespace-pre-wrap"><code>{block.lines.join('\n').trim()}</code></pre>
          </div>;
        }
        if (block.type === 'heading') {
          const Heading = block.level === 1 ? 'h3' : 'h4';
          return <Heading key={index} className="text-white font-bold mt-4 first:mt-0">{renderInline(block.text)}</Heading>;
        }
        if (block.type === 'ul' || block.type === 'ol') {
          const List = block.type;
          return <List key={index} className={`${block.type === 'ul' ? 'list-disc' : 'list-decimal'} space-y-1.5 pl-5 text-slate-200`}>
            {block.items.map((item, itemIndex) => <li key={itemIndex}>{renderInline(item)}</li>)}
          </List>;
        }
        return <p key={index} className="text-slate-200">{renderInline(block.text)}</p>;
      })}
    </div>
  );
};

const CareerAgentPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { conversationId: urlConversationId } = useParams();

  // Conversations and Messages
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(urlConversationId || null);
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetchingHistory, setFetchingHistory] = useState(false);
  const [errorState, setErrorState] = useState(null);
  const [lastFailedMessage, setLastFailedMessage] = useState(null);

  // Inline rename state
  const [editingConvId, setEditingConvId] = useState(null);
  const [editingTitle, setEditingTitle] = useState('');

  // Sidebar toggle for mobile/tablet
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  // Load conversation list on mount
  const loadConversations = async () => {
    try {
      const res = await agentService.getConversations();
      let list = res.data || [];
      const localKey = `career_chat_titles_${user?.id || 'default'}`;
      try {
        const cached = JSON.parse(localStorage.getItem(localKey) || '{}');
        list = list.map(c => ({
          ...c,
          title: cached[c.id] || c.title || 'Conversation'
        }));
      } catch (_) {}
      setConversations(list);
      return list;
    } catch (err) {
      console.error('Failed to load conversations', err);
      return [];
    }
  };

  useEffect(() => {
    loadConversations().then((list) => {
      if (urlConversationId) {
        selectConversation(urlConversationId);
      } else if (list.length > 0) {
        selectConversation(list[0].id);
      }
    });
  }, []);

  // Sync URL changes
  useEffect(() => {
    if (urlConversationId && urlConversationId !== activeConversationId) {
      selectConversation(urlConversationId);
    }
  }, [urlConversationId]);

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 180)}px`;
    }
  }, [inputMessage]);

  const selectConversation = async (convId) => {
    setActiveConversationId(convId);
    setErrorState(null);
    setFetchingHistory(true);
    setSidebarOpen(false);

    try {
      const res = await agentService.getConversationMessages(convId);
      setMessages(res.data || []);
      navigate(`/career-agent/${convId}`, { replace: true });
    } catch (err) {
      console.error('Failed to load messages for conversation', convId, err);
      setMessages([]);
    } finally {
      setFetchingHistory(false);
    }
  };

  const startNewChat = async () => {
    try {
      const res = await agentService.createConversation();
      const newId = res.data?.conversationId || ('conv-' + Date.now());
      setActiveConversationId(newId);
      setMessages([]);
      setErrorState(null);
      setInputMessage('');
      setSidebarOpen(false);
      navigate(`/career-agent/${newId}`);
    } catch (err) {
      const fallbackId = 'conv-' + Date.now();
      setActiveConversationId(fallbackId);
      setMessages([]);
      setErrorState(null);
      setInputMessage('');
      setSidebarOpen(false);
      navigate(`/career-agent/${fallbackId}`);
    }
  };

  const handleDeleteConversation = async (e, convId) => {
    e.stopPropagation();
    try {
      await agentService.deleteConversation(convId);
      toast.success('Conversation removed');
      const updated = conversations.filter(c => c.id !== convId);
      setConversations(updated);

      if (activeConversationId === convId) {
        if (updated.length > 0) {
          selectConversation(updated[0].id);
        } else {
          startNewChat();
        }
      }
    } catch (err) {
      toast.error('Could not delete conversation');
    }
  };

  const handleSendMessage = async (textToSend) => {
    const text = (textToSend || inputMessage).trim();
    if (!text || loading) return;

    const convId = activeConversationId || ('conv-' + Date.now());
    if (!activeConversationId) {
      setActiveConversationId(convId);
    }

    // Optimistically push user message
    const userMsg = { role: 'user', content: text, createdAt: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);
    setInputMessage('');
    setErrorState(null);
    setLoading(true);

    try {
      const res = await agentService.chat({
        conversationId: convId,
        message: text
      });

      const responseText = res.data?.response || "I have analyzed your verified data.";
      const assistantMsg = { role: 'assistant', content: responseText, createdAt: new Date().toISOString() };
      setMessages(prev => [...prev, assistantMsg]);

      // Refresh conversations list to show updated title/timestamp
      loadConversations();
    } catch (err) {
      console.error('Agent chat error:', err);
      const errMsg = typeof err.response?.data === 'string'
        ? err.response.data
        : (err.response?.data?.error || err.message || 'AI service temporarily unavailable');
      
      setErrorState({
        message: errMsg,
        failedQuery: text
      });
      setLastFailedMessage(text);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleStartRename = (e, conv) => {
    e.stopPropagation();
    setEditingConvId(conv.id);
    setEditingTitle(conv.title || 'Conversation');
  };

  const handleSaveRename = async (e, convId) => {
    if (e) e.stopPropagation();
    const cleanTitle = editingTitle.trim();
    if (!cleanTitle) {
      setEditingConvId(null);
      return;
    }

    // Optimistically update conversation list in state
    setConversations(prev => prev.map(c => c.id === convId ? { ...c, title: cleanTitle } : c));
    setEditingConvId(null);

    // Save locally for instant recall
    try {
      const localKey = `career_chat_titles_${user?.id || 'default'}`;
      const cached = JSON.parse(localStorage.getItem(localKey) || '{}');
      cached[convId] = cleanTitle;
      localStorage.setItem(localKey, JSON.stringify(cached));
    } catch (_) {}

    // Persist to backend
    try {
      await agentService.renameConversation(convId, cleanTitle);
      toast.success('Conversation renamed');
    } catch (err) {
      console.warn('Backend rename sync error:', err);
      toast.success('Conversation renamed');
    }
  };

  const handleCancelRename = (e) => {
    if (e) e.stopPropagation();
    setEditingConvId(null);
    setEditingTitle('');
  };

  const renderConversationItem = (c) => {
    const isActive = activeConversationId === c.id;
    const isEditing = editingConvId === c.id;

    if (isEditing) {
      return (
        <div 
          key={c.id} 
          onClick={(e) => e.stopPropagation()}
          className="p-1.5 rounded-xl bg-slate-850 border border-violet-500/50 flex items-center gap-1.5 my-1"
        >
          <input
            type="text"
            value={editingTitle}
            onChange={(e) => setEditingTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleSaveRename(e, c.id);
              } else if (e.key === 'Escape') {
                e.preventDefault();
                handleCancelRename(e);
              }
            }}
            autoFocus
            className="flex-1 bg-slate-950 text-white text-xs px-2 py-1.5 rounded-lg border border-slate-700 focus:outline-none focus:border-violet-400"
          />
          <button
            onClick={(e) => handleSaveRename(e, c.id)}
            className="p-1.5 text-emerald-400 hover:bg-emerald-500/20 rounded-lg transition-colors cursor-pointer"
            title="Save name"
          >
            <Check className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleCancelRename}
            className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-700/50 rounded-lg transition-colors cursor-pointer"
            title="Cancel"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      );
    }

    return (
      <div
        key={c.id}
        onClick={() => selectConversation(c.id)}
        className={`
          group relative flex items-center justify-between p-2.5 rounded-xl cursor-pointer text-xs font-medium transition-all
          ${isActive 
            ? 'bg-violet-600/20 text-white border border-violet-500/30 shadow-sm' 
            : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200 border border-transparent'}
        `}
      >
        <div className="flex items-center gap-2.5 min-w-0 pr-14">
          <MessageSquare className={`w-3.5 h-3.5 shrink-0 ${isActive ? 'text-violet-400' : 'text-slate-500 group-hover:text-slate-400'}`} />
          <span className="truncate" title={c.title || 'Conversation'}>
            {c.title || 'Conversation'}
          </span>
        </div>

        {/* Action icons: Rename & Delete */}
        <div className="absolute right-2 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={(e) => handleStartRename(e, c)}
            className="p-1.5 text-slate-400 hover:text-violet-300 hover:bg-slate-700/70 rounded-lg transition-colors cursor-pointer"
            title="Rename Chat"
          >
            <Edit3 className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={(e) => handleDeleteConversation(e, c.id)}
            className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-700/70 rounded-lg transition-colors cursor-pointer"
            title="Delete Chat"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  };

  // Group conversations by date (Today, Yesterday, Older)
  const groupedConversations = () => {
    const today = [];
    const yesterday = [];
    const older = [];

    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const yesterdayStart = todayStart - (24 * 60 * 60 * 1000);

    conversations.forEach(c => {
      const time = c.updatedAt ? new Date(c.updatedAt).getTime() : (c.createdAt ? new Date(c.createdAt).getTime() : 0);
      if (time >= todayStart) {
        today.push(c);
      } else if (time >= yesterdayStart) {
        yesterday.push(c);
      } else {
        older.push(c);
      }
    });

    return { today, yesterday, older };
  };

  const { today, yesterday, older } = groupedConversations();

  return (
    <div className="flex h-screen bg-slate-950 text-slate-100 overflow-hidden select-none">
      
      {/* ============================================================ */}
      {/* MOBILE BACKDROP DRAWER                                       */}
      {/* ============================================================ */}
      {sidebarOpen && (
        <div 
          onClick={() => setSidebarOpen(false)}
          className="fixed inset-0 z-40 bg-slate-950/80 backdrop-blur-sm lg:hidden transition-opacity"
        />
      )}

      {/* ============================================================ */}
      {/* SIDEBAR                                                      */}
      {/* ============================================================ */}
      <aside className={`
        fixed lg:static top-0 bottom-0 left-0 z-50
        w-72 bg-slate-900/90 border-r border-slate-800/80 flex flex-col
        transition-transform duration-300 ease-in-out
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        {/* Sidebar Header */}
        <div className="p-4 border-b border-slate-800/60 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400">
              <Brain className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-black text-sm tracking-tight text-white flex items-center gap-1.5">
                Career Agent
                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-violet-600/20 text-violet-400 border border-violet-500/30 font-bold uppercase">
                  AI
                </span>
              </h2>
            </div>
          </div>
          <button 
            onClick={() => setSidebarOpen(false)}
            className="lg:hidden p-1.5 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* New Chat Button */}
        <div className="p-3">
          <button
            onClick={startNewChat}
            className="w-full py-3 px-4 rounded-2xl bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-violet-600/20 transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" /> New Chat
          </button>
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto px-3 py-2 space-y-4 select-text">
          {conversations.length === 0 ? (
            <div className="text-center py-10 px-4">
              <MessageSquare className="w-8 h-8 text-slate-700 mx-auto mb-2 opacity-50" />
              <p className="text-xs text-slate-500 font-medium">No previous chats yet.</p>
              <p className="text-[11px] text-slate-600 mt-1">Start a new conversation to explore your verified scores and roadmap.</p>
            </div>
          ) : (
            <>
              {today.length > 0 && (
                <div>
                  <div className="px-3 py-1 text-[11px] font-extrabold uppercase tracking-wider text-slate-500">Today</div>
                  <div className="space-y-1 mt-1">
                    {today.map(c => renderConversationItem(c))}
                  </div>
                </div>
              )}

              {yesterday.length > 0 && (
                <div>
                  <div className="px-3 py-1 text-[11px] font-extrabold uppercase tracking-wider text-slate-500">Yesterday</div>
                  <div className="space-y-1 mt-1">
                    {yesterday.map(c => renderConversationItem(c))}
                  </div>
                </div>
              )}

              {older.length > 0 && (
                <div>
                  <div className="px-3 py-1 text-[11px] font-extrabold uppercase tracking-wider text-slate-500">Older</div>
                  <div className="space-y-1 mt-1">
                    {older.map(c => renderConversationItem(c))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Sidebar Footer: Back to Dashboard */}
        <div className="p-3 border-t border-slate-800/60 bg-slate-900/50">
          <Link
            to="/dashboard"
            className="w-full py-2.5 px-3 rounded-xl border border-slate-800 hover:bg-slate-800 text-slate-300 hover:text-white text-xs font-semibold flex items-center gap-2 transition-colors"
          >
            <ArrowLeft className="w-4 h-4 text-slate-400" />
            <span>Return to Dashboard</span>
          </Link>
        </div>
      </aside>

      {/* ============================================================ */}
      {/* MAIN CHAT AREA                                               */}
      {/* ============================================================ */}
      <main className="flex-1 flex flex-col h-full bg-slate-950 relative overflow-hidden">
        
        {/* Top Navigation Bar */}
        <header className="h-16 px-4 md:px-6 border-b border-slate-800/80 bg-slate-950/70 backdrop-blur-md flex items-center justify-between shrink-0 z-10">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setSidebarOpen(true)}
              className="lg:hidden p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-900"
              aria-label="Open sidebar"
            >
              <Menu className="w-5 h-5" />
            </button>
            
            <div className="flex items-center gap-2.5">
              <h1 className="font-bold text-base md:text-lg text-white">
                Personal Career Agent
              </h1>
              <span className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[11px] font-semibold">
                <ShieldCheck className="w-3 h-3" /> Personalized Career Guidance
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={startNewChat}
              className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-800 bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs font-semibold transition-colors"
            >
              <Plus className="w-3.5 h-3.5 text-violet-400" /> New Chat
            </button>
          </div>
        </header>

        {/* Message Stream */}
        <div className="flex-1 overflow-y-auto px-4 md:px-8 py-6 space-y-6 select-text">
          {fetchingHistory ? (
            <div className="h-full flex flex-col items-center justify-center space-y-3">
              <RefreshCw className="w-8 h-8 text-violet-500 animate-spin" />
              <p className="text-sm text-slate-400 font-medium">Retrieving conversation history...</p>
            </div>
          ) : messages.length === 0 ? (
            /* Welcome / Zero State with Prompts */
            <div className="max-w-3xl mx-auto pt-8 pb-12 text-center space-y-8 animate-in fade-in duration-300">
              <div className="space-y-3">
                <div className="w-16 h-16 rounded-3xl bg-gradient-to-tr from-violet-600 to-indigo-600 p-0.5 mx-auto shadow-xl shadow-violet-600/20">
                  <div className="w-full h-full bg-slate-950 rounded-[22px] flex items-center justify-center text-violet-400">
                    <Brain className="w-8 h-8" />
                  </div>
                </div>
                <h2 className="text-2xl md:text-3xl font-black text-white">
                  Welcome, {user?.firstName || (user?.name ? user.name.split(' ')[0] : 'Candidate')}!
                </h2>
                <p className="text-sm text-slate-400 max-w-xl mx-auto leading-relaxed">
                  Hi! I'm your Career Advisor. I can help you understand your skills, interview performance, resume, and career progress. What would you like to work on?
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-left">
                {SUGGESTION_PROMPTS.map((item, idx) => (
                  <div
                    key={idx}
                    onClick={() => handleSendMessage(item.prompt)}
                    className="group p-4 rounded-2xl bg-slate-900/60 border border-slate-850 hover:border-violet-500/50 hover:bg-slate-900 transition-all duration-200 cursor-pointer flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs font-bold text-violet-400 flex items-center gap-1.5">
                          <Sparkles className="w-3.5 h-3.5" /> {item.title}
                        </span>
                      </div>
                      <p className="text-sm font-semibold text-slate-200 group-hover:text-white transition-colors">
                        "{item.prompt}"
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-2">{item.desc}</p>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            /* Message List */
            <div className="max-w-3xl mx-auto space-y-6">
              {messages.map((msg, index) => {
                const isUser = msg.role === 'user' || msg.type === 'USER';
                return (
                  <div 
                    key={index}
                    className={`flex items-start gap-3.5 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
                  >
                    {/* Avatar */}
                    <div className={`
                      w-9 h-9 rounded-2xl flex items-center justify-center shrink-0 text-sm font-bold shadow-md
                      ${isUser 
                        ? 'bg-gradient-to-tr from-violet-600 to-fuchsia-600 text-white' 
                        : 'bg-slate-850 border border-slate-750 text-indigo-400'}
                    `}>
                      {isUser ? <User className="w-5 h-5" /> : <Bot className="w-5 h-5" />}
                    </div>

                    {/* Message Bubble */}
                    <div className={`
                      max-w-[85%] rounded-3xl p-4 md:p-5 shadow-lg
                      ${isUser 
                        ? 'bg-gradient-to-r from-violet-600 to-indigo-600 text-white rounded-tr-sm' 
                        : 'bg-slate-900/80 border border-slate-800 text-slate-200 rounded-tl-sm'}
                    `}>
                      {isUser ? (
                        <p className="text-sm md:text-base font-medium whitespace-pre-wrap leading-relaxed">{msg.content}</p>
                      ) : (
                        <FormattedMessage content={msg.content} />
                      )}
                    </div>
                  </div>
                );
              })}

              {/* Typing Indicator */}
              {loading && (
                <div className="flex items-start gap-3.5 animate-in fade-in duration-200">
                  <div className="w-9 h-9 rounded-2xl bg-slate-850 border border-slate-750 flex items-center justify-center text-indigo-400 shrink-0">
                    <Bot className="w-5 h-5" />
                  </div>
                  <div className="bg-slate-900 border border-slate-800 rounded-3xl rounded-tl-sm px-5 py-4 text-sm text-slate-400 flex items-center gap-3">
                    <div className="flex items-center gap-1.5">
                      <div className="w-2 h-2 rounded-full bg-violet-500 animate-bounce" style={{ animationDelay: '0ms' }}></div>
                      <div className="w-2 h-2 rounded-full bg-violet-500 animate-bounce" style={{ animationDelay: '150ms' }}></div>
                      <div className="w-2 h-2 rounded-full bg-violet-500 animate-bounce" style={{ animationDelay: '300ms' }}></div>
                    </div>
                    <span className="text-xs font-medium">Reviewing your career data...</span>
                  </div>
                </div>
              )}

              {/* Error Banner with Retry */}
              {errorState && (
                <div className="p-4 rounded-2xl border border-red-500/30 bg-red-950/30 text-red-300 text-sm flex items-center justify-between gap-3 animate-in fade-in">
                  <div className="flex items-center gap-2.5">
                    <AlertCircle className="w-5 h-5 text-red-400 shrink-0" />
                    <div>
                      <p className="font-semibold text-red-200">Execution Error</p>
                      <p className="text-xs text-red-300/80">{errorState.message}</p>
                    </div>
                  </div>
                  {lastFailedMessage && (
                    <button
                      onClick={() => handleSendMessage(lastFailedMessage)}
                      className="px-3 py-1.5 rounded-xl bg-red-900/50 hover:bg-red-800 border border-red-700/50 text-xs font-bold text-white flex items-center gap-1.5 transition-colors"
                    >
                      <RefreshCw className="w-3.5 h-3.5" /> Retry
                    </button>
                  )}
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* Message Composer Footer */}
        <footer className="p-4 md:p-6 border-t border-slate-800/80 bg-slate-950/80 backdrop-blur-md shrink-0">
          <div className="max-w-3xl mx-auto space-y-2">
            <div className="relative flex items-end rounded-3xl bg-slate-900/90 border border-slate-800 focus-within:border-violet-500/60 focus-within:ring-1 focus-within:ring-violet-500/50 transition-all p-2 shadow-2xl">
              <textarea
                ref={textareaRef}
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Ask about your weaknesses, interview preparation, scores, or roadmap..."
                rows={1}
                disabled={loading}
                className="w-full bg-transparent text-slate-100 placeholder-slate-500 text-sm md:text-base px-4 py-2 resize-none focus:outline-none max-h-44 disabled:opacity-50"
              />
              <button
                onClick={() => handleSendMessage()}
                disabled={!inputMessage.trim() || loading}
                className={`
                  p-3 rounded-2xl shrink-0 transition-all
                  ${inputMessage.trim() && !loading
                    ? 'bg-violet-600 hover:bg-violet-500 text-white shadow-md shadow-violet-600/30 cursor-pointer'
                    : 'bg-slate-800 text-slate-600 cursor-not-allowed'}
                `}
                aria-label="Send message"
              >
                {loading ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
              </button>
            </div>
            
            <p className="text-[11px] text-center text-slate-500 font-medium">
              Grounds advice in your stored assessments, resume projects, and mock interviews with Zero Hallucination.
            </p>
          </div>
        </footer>

      </main>
    </div>
  );
};

export default CareerAgentPage;


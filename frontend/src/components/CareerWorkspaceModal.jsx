import { useState, useEffect } from 'react';
import { workspaceService } from '../services/api';
import { 
  X, Folder, FileText, RefreshCw, ChevronRight, FileCode, 
  Copy, Check, Sparkles, FolderOpen, Download, ZoomIn, ZoomOut, 
  Trash2, Plus, FilePlus2 
} from 'lucide-react';
import toast from 'react-hot-toast';

const QUICK_FILE_TEMPLATES = [
  {
    name: 'Custom-Cover-Letter.md',
    folder: 'Resume',
    label: 'Tailored Cover Letter',
    icon: '📄',
    prompt: 'Generate a highly tailored, professional software engineer cover letter matching my target role and highlighted projects without bracketed placeholders.'
  },
  {
    name: 'Kafka-Event-Driven-Guide.md',
    folder: 'Interview',
    label: 'Kafka & Event-Driven Architecture',
    icon: '⚡',
    prompt: 'Create a deep-dive technical interview guide on Apache Kafka: topics, partition keys, consumer groups, offset commits, at-least-once processing, and common pitfalls in Java applications.'
  },
  {
    name: 'Java-Concurrency-Playbook.md',
    folder: 'Interview',
    label: 'Java Multithreading & Memory Model',
    icon: '☕',
    prompt: 'Explain the Java Memory Model, volatile happens-before semantics, synchronized vs ReentrantLock, deadlock prevention, and ConcurrentHashMap internal mechanics with code snippets.'
  },
  {
    name: 'Spring-Security-JWT-Notes.md',
    folder: 'Interview',
    label: 'Spring Security & JWT Breakdown',
    icon: '🔒',
    prompt: 'Provide an architectural guide to Spring Security 6 filter chain, OncePerRequestFilter, JWT token creation/validation, and CORS/CSRF handling for stateless REST APIs.'
  },
  {
    name: 'Top-DSA-Patterns.md',
    folder: 'Preparation',
    label: 'DSA Top Patterns & Cheatsheet',
    icon: '🧠',
    prompt: 'Compile a high-yield DSA cheat sheet covering Two Pointers, Sliding Window, Monotonic Stack, BFS/DFS tree traversals, and dynamic programming state transitions with Java examples.'
  },
  {
    name: 'Salary-Negotiation-Script.md',
    folder: 'Preparation',
    label: 'Salary & Offer Negotiation Script',
    icon: '💼',
    prompt: 'Provide realistic negotiation email scripts, phone call talking points, and market rate positioning for software engineering job offers.'
  }
];

const CareerWorkspaceModal = ({ isOpen, onClose }) => {
  const [files, setFiles] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileContent, setFileContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [fileLoading, setFileLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [documentZoom, setDocumentZoom] = useState(100);

  // Custom AI file generation state
  const [showCustomModal, setShowCustomModal] = useState(false);
  const [customFileName, setCustomFileName] = useState('');
  const [customFolder, setCustomFolder] = useState('Interview');
  const [customPrompt, setCustomPrompt] = useState('');
  const [customLoading, setCustomLoading] = useState(false);

  // Confirmation state
  const [showClearConfirm, setShowClearConfirm] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadFiles();
    }
  }, [isOpen]);

  const loadFiles = async () => {
    setLoading(true);
    try {
      const res = await workspaceService.listFiles();
      const fileList = res.data || [];
      setFiles(fileList);
      if (fileList.length > 0) {
        handleSelectFile(fileList[0]);
      } else {
        setSelectedFile(null);
        setFileContent('');
      }
    } catch (err) {
      console.warn('Could not list workspace files:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    setLoading(true);
    try {
      const res = await workspaceService.generate();
      const generated = res.data?.generatedFiles || [];
      setFiles(generated);
      toast.success('AI Career Workspace compiled successfully!');
      if (generated.length > 0) {
        handleSelectFile(generated[0]);
      }
    } catch (err) {
      toast.error('Failed to generate workspace files.');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectFile = async (path) => {
    setSelectedFile(path);
    setFileLoading(true);
    try {
      const res = await workspaceService.readFile(path);
      setFileContent(typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2));
    } catch (err) {
      setFileContent('Unable to load file content.');
    } finally {
      setFileLoading(false);
    }
  };

  const handleDeleteFile = async (filePath) => {
    try {
      await workspaceService.deleteFile(filePath);
      const remaining = files.filter(f => f !== filePath);
      setFiles(remaining);
      toast.success('File deleted');
      if (selectedFile === filePath) {
        if (remaining.length > 0) {
          handleSelectFile(remaining[0]);
        } else {
          setSelectedFile(null);
          setFileContent('');
        }
      }
    } catch (err) {
      toast.error('Failed to delete file.');
    }
  };

  const handleClearWorkspace = async () => {
    try {
      await workspaceService.clearWorkspace();
      setFiles([]);
      setSelectedFile(null);
      setFileContent('');
      setShowClearConfirm(false);
      toast.success('Workspace cleared');
    } catch (err) {
      toast.error('Failed to clear workspace.');
    }
  };

  const handleCreateCustomFile = async (e) => {
    e?.preventDefault();
    if (!customFileName.trim() || !customPrompt.trim() || customLoading) return;
    setCustomLoading(true);
    try {
      const res = await workspaceService.createCustomFile({
        fileName: customFileName.trim(),
        folder: customFolder,
        prompt: customPrompt.trim()
      });
      const newPath = res.data?.filePath || `${customFolder}/${customFileName.trim()}`;
      toast.success(`Generated "${res.data?.fileName || customFileName.trim()}"`);
      setShowCustomModal(false);
      setCustomFileName('');
      setCustomPrompt('');

      const listRes = await workspaceService.listFiles();
      const updatedList = listRes.data || [];
      setFiles(updatedList);
      handleSelectFile(newPath);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to generate custom file with AI.');
    } finally {
      setCustomLoading(false);
    }
  };

  const handleCopy = () => {
    if (!fileContent) return;
    navigator.clipboard.writeText(fileContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    toast.success('Copied to clipboard');
  };

  const handleDownload = async (path) => {
    try {
      const res = await workspaceService.readFile(path);
      const content = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2);
      const fileName = path.split('/').pop() || 'workspace-artifact.md';
      const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
      const downloadUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = downloadUrl;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(downloadUrl);
      toast.success(`${fileName} downloaded`);
    } catch (err) {
      toast.error('Could not download this workspace file.');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/95 backdrop-blur-md animate-in fade-in duration-200">
      <div className="bg-slate-900 w-screen h-screen flex flex-col shadow-2xl overflow-hidden relative text-white">
        
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-900/80">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-amber-500/15 border border-amber-500/30 rounded-xl flex items-center justify-center text-amber-400">
              <Folder className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold">AI Career Workspace</h3>
                <span className="text-[10px] uppercase font-black tracking-wider bg-amber-500/20 text-amber-300 border border-amber-500/30 px-2 py-0.5 rounded-full flex items-center gap-1">
                  <Sparkles className="w-3 h-3" /> Live Artifacts
                </span>
              </div>
              <p className="text-xs text-slate-400">Isolated workspace preparation notes, resume analytics, and custom AI guides</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowCustomModal(true)}
              className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-amber-300 border border-amber-500/30 font-bold rounded-xl text-xs transition-all flex items-center gap-1.5 shadow-sm cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5 text-amber-400" />
              + New AI File
            </button>
            <button
              onClick={handleGenerate}
              disabled={loading}
              className="px-3.5 py-1.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl text-xs transition-all flex items-center gap-1.5 shadow-sm cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              {loading ? 'Compiling...' : 'Regenerate Workspace'}
            </button>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white p-2 rounded-full hover:bg-slate-800 transition-all cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Main Workspace Body */}
        {files.length === 0 && !loading ? (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center space-y-5">
            <div className="w-16 h-16 bg-amber-500/10 border border-amber-500/20 rounded-2xl flex items-center justify-center text-amber-400">
              <FolderOpen className="w-8 h-8" />
            </div>
            <div className="max-w-md space-y-2">
              <h4 className="text-xl font-bold text-slate-100">No Workspace Artifacts Found</h4>
              <p className="text-sm text-slate-400 leading-relaxed">
                Compile your verified profile data, assessment results, and weak concepts into structured documents, or prompt the AI to generate a custom document.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={handleGenerate}
                disabled={loading}
                className="px-6 py-3 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black rounded-xl text-sm transition-all shadow-lg cursor-pointer disabled:opacity-50 flex items-center gap-2"
              >
                <Sparkles className="w-4 h-4" />
                Compile Workspace
              </button>
              <button
                onClick={() => setShowCustomModal(true)}
                className="px-5 py-3 bg-slate-800 hover:bg-slate-700 text-amber-300 border border-amber-500/30 font-bold rounded-xl text-sm transition-all cursor-pointer flex items-center gap-2"
              >
                <FilePlus2 className="w-4 h-4" />
                + Create Custom File with AI
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex overflow-hidden">
            {/* Sidebar / File Explorer */}
            <div className="w-76 border-r border-slate-800 bg-slate-950/50 flex flex-col shrink-0">
              <div className="px-4 py-3 border-b border-slate-800/80 flex items-center justify-between text-xs font-semibold text-slate-400">
                <span>EXPLORER</span>
                <div className="flex items-center gap-2">
                  <span className="text-[10px] text-amber-400 font-mono bg-amber-400/10 px-1.5 py-0.5 rounded">
                    {files.length} {files.length === 1 ? 'file' : 'files'}
                  </span>
                  {files.length > 0 && (
                    <button
                      onClick={() => setShowClearConfirm(true)}
                      title="Clear all workspace files"
                      className="p-1 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>

              {/* Action Button: + New AI File */}
              <div className="p-3 border-b border-slate-800/60">
                <button
                  onClick={() => setShowCustomModal(true)}
                  className="w-full py-2 px-3 rounded-xl bg-gradient-to-r from-amber-500/15 to-orange-500/15 hover:from-amber-500/25 hover:to-orange-500/25 text-amber-300 border border-amber-500/30 font-semibold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer shadow-sm"
                >
                  <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                  + New AI File on Demand
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-3 space-y-1">
                {files.map((filePath, i) => {
                  const isSelected = selectedFile === filePath;
                  const parts = filePath.split('/');
                  const fileName = parts[parts.length - 1];
                  const folderName = parts.length > 1 ? parts.slice(0, -1).join('/') : '';

                  return (
                    <div
                      key={i}
                      className={`group w-full rounded-xl text-xs flex items-center gap-1 transition-all ${
                        isSelected
                          ? 'bg-amber-500/15 text-amber-300 border border-amber-500/30 font-medium'
                          : 'text-slate-300 hover:bg-slate-800/60 hover:text-slate-100 border border-transparent'
                      }`}
                    >
                      <button
                        type="button"
                        onClick={() => handleSelectFile(filePath)}
                        className="flex-1 min-w-0 flex items-center gap-2.5 text-left px-3 py-2.5 cursor-pointer"
                      >
                        <FileText className={`w-4 h-4 shrink-0 ${isSelected ? 'text-amber-400' : 'text-slate-500'}`} />
                        <span className="flex-1 min-w-0">
                          <span className="block truncate font-mono">{fileName}</span>
                          {folderName && (
                            <span className="block text-[10px] text-slate-500 truncate">{folderName}</span>
                          )}
                        </span>
                        <ChevronRight className={`w-3.5 h-3.5 shrink-0 ${isSelected ? 'text-amber-400' : 'opacity-0'}`} />
                      </button>

                      <div className="flex items-center gap-0.5 mr-1.5 shrink-0">
                        <button
                          type="button"
                          onClick={() => handleDownload(filePath)}
                          title={`Download ${fileName}`}
                          aria-label={`Download ${fileName}`}
                          className="p-1.5 rounded-lg text-slate-500 hover:text-amber-300 hover:bg-amber-500/10 cursor-pointer transition-colors"
                        >
                          <Download className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteFile(filePath)}
                          title={`Delete ${fileName}`}
                          aria-label={`Delete ${fileName}`}
                          className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 cursor-pointer transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Content / Markdown Previewer */}
            <div className="flex-1 flex flex-col bg-slate-950 overflow-hidden">
              {selectedFile ? (
                <>
                  {/* File Header Bar */}
                  <div className="px-6 py-3 border-b border-slate-800/80 bg-slate-900/40 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2 text-slate-400 font-mono truncate mr-3">
                      <FileCode className="w-4 h-4 text-amber-400 shrink-0" />
                      <span className="text-slate-200 font-bold truncate">{selectedFile}</span>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <div className="flex items-center gap-1 rounded-lg border border-slate-800 bg-slate-950/70 p-0.5">
                        <button
                          type="button"
                          onClick={() => setDocumentZoom(value => Math.max(70, value - 10))}
                          disabled={documentZoom <= 70}
                          title="Zoom out"
                          aria-label="Zoom out"
                          className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          <ZoomOut className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setDocumentZoom(100)}
                          title="Reset zoom"
                          aria-label="Reset zoom"
                          className="min-w-10 px-1 text-[10px] font-bold text-amber-300 hover:text-white cursor-pointer"
                        >
                          {documentZoom}%
                        </button>
                        <button
                          type="button"
                          onClick={() => setDocumentZoom(value => Math.min(160, value + 10))}
                          disabled={documentZoom >= 160}
                          title="Zoom in"
                          aria-label="Zoom in"
                          className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          <ZoomIn className="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <button
                        onClick={handleCopy}
                        className="flex items-center gap-1.5 px-3 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg text-[11px] transition-all cursor-pointer"
                      >
                        {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        {copied ? 'Copied' : 'Copy'}
                      </button>
                      <button
                        onClick={() => handleDeleteFile(selectedFile)}
                        className="flex items-center gap-1 px-2.5 py-1 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg text-[11px] transition-all cursor-pointer"
                        title="Delete this file"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        <span className="hidden sm:inline">Delete</span>
                      </button>
                    </div>
                  </div>

                  {/* Document Body */}
                  <div className="flex-1 overflow-y-auto p-6 text-sm leading-relaxed text-slate-200 font-sans">
                    {fileLoading ? (
                      <div className="flex items-center justify-center h-full text-slate-500 text-xs gap-2">
                        <RefreshCw className="w-4 h-4 animate-spin text-amber-400" /> Loading artifact...
                      </div>
                    ) : (
                      <div className="prose prose-invert max-w-none space-y-4">
                        <pre
                          className="p-5 bg-slate-900 border border-slate-800/80 rounded-2xl font-mono text-xs text-amber-200/90 whitespace-pre-wrap leading-relaxed overflow-x-auto shadow-inner"
                          style={{ fontSize: `${0.75 * documentZoom / 100}rem` }}
                        >
                          {fileContent}
                        </pre>
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <div className="flex-1 flex items-center justify-center text-slate-500 text-sm">
                  Select a file from the explorer on the left.
                </div>
              )}
            </div>
          </div>
        )}

      </div>

      {/* ============================================================ */}
      {/* CUSTOM AI FILE CREATION MODAL                                */}
      {/* ============================================================ */}
      {showCustomModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl p-6 space-y-4 text-white">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-2xl bg-amber-500/20 border border-amber-500/30 text-amber-400 flex items-center justify-center">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-white">Generate Custom Document with AI</h4>
                  <p className="text-xs text-slate-400">Describe the topics, code patterns, or questions you need</p>
                </div>
              </div>
              <button
                onClick={() => setShowCustomModal(false)}
                className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Quick Inspiration Chips */}
            <div>
              <label className="text-[10px] font-bold uppercase text-slate-400 tracking-wider block mb-1.5">
                Quick Inspiration Templates (Click to Autofill)
              </label>
              <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto">
                {QUICK_FILE_TEMPLATES.map((tmpl, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => {
                      setCustomFileName(tmpl.name);
                      setCustomFolder(tmpl.folder);
                      setCustomPrompt(tmpl.prompt);
                    }}
                    className="text-[11px] px-2.5 py-1 bg-slate-800/90 hover:bg-amber-500/20 text-slate-300 hover:text-amber-300 border border-slate-700/80 hover:border-amber-500/40 rounded-lg transition-all cursor-pointer flex items-center gap-1"
                  >
                    <span>{tmpl.icon}</span> {tmpl.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Inputs */}
            <div className="grid grid-cols-3 gap-3">
              <div className="col-span-2 space-y-1">
                <label className="text-xs font-semibold text-slate-300">File Name (.md)</label>
                <input
                  type="text"
                  placeholder="e.g. Kafka-CheatSheet.md"
                  value={customFileName}
                  onChange={(e) => setCustomFileName(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-amber-400 font-mono"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-300">Target Folder</label>
                <select
                  value={customFolder}
                  onChange={(e) => setCustomFolder(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-400"
                >
                  <option value="Interview">Interview/</option>
                  <option value="Preparation">Preparation/</option>
                  <option value="Resume">Resume/</option>
                  <option value="Custom">Custom/</option>
                </select>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-300">
                What should this file contain? (Prompt / Instructions)
              </label>
              <textarea
                rows={4}
                placeholder="e.g. Provide a deep-dive technical note on Spring Security filter chains, JWT validation, and CORS handling with production code snippets..."
                value={customPrompt}
                onChange={(e) => setCustomPrompt(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 rounded-xl p-3 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-400 resize-none"
              />
            </div>

            {/* Actions */}
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setShowCustomModal(false)}
                className="px-4 py-2 text-xs text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!customFileName.trim() || !customPrompt.trim() || customLoading}
                onClick={handleCreateCustomFile}
                className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold rounded-xl text-xs flex items-center gap-1.5 transition-all shadow-md cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {customLoading ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Generating with AI...
                  </>
                ) : (
                  <>
                    <Sparkles className="w-3.5 h-3.5" /> Generate Document
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================ */}
      {/* CLEAR WORKSPACE CONFIRMATION MODAL                           */}
      {/* ============================================================ */}
      {showClearConfirm && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-sm shadow-2xl p-5 space-y-4 text-white">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-rose-500/20 text-rose-400 flex items-center justify-center shrink-0">
                <Trash2 className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white">Clear All Workspace Files?</h4>
                <p className="text-xs text-slate-400">This will delete all documents in your current workspace directory.</p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={() => setShowClearConfirm(false)}
                className="px-3.5 py-1.5 text-xs text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleClearWorkspace}
                className="px-4 py-1.5 bg-rose-600 hover:bg-rose-500 text-white font-bold rounded-xl text-xs transition-colors cursor-pointer"
              >
                Yes, Clear All
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export default CareerWorkspaceModal;

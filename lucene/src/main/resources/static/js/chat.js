(() => {
    const ACTIVE_SESSION_KEY = 'prism-active-session';
    const body = document.body;
    const scopeType = body.dataset.scopeType;
    let scopeId = body.dataset.scopeId || null;

    const chatWindow = document.getElementById('chat-window');
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const citationLinks = document.getElementById('citation-links');

    let activeSessionId = null;
    const documentsCache = {};

    const Toast = window.PrismToast || { info: () => {}, success: () => {}, error: () => {}, warning: () => {}, progress: () => ({ update: () => {}, close: () => {} }) };
    const Drop = window.PrismDrop || { attach: () => {} };

    const escapeHtml = (value) => String(value ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

    const scrollToBottom = () => { if (chatWindow) chatWindow.scrollTop = chatWindow.scrollHeight; };

    // ─── Chat rendering ────────────────────────────────────────────
    const renderMessage = (role, text, citations = []) => {
        if (!chatWindow) return;
        const wrap = document.createElement('div');
        wrap.className = `message ${role === 'user' ? 'user' : 'bot'}`;
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        const p = document.createElement('p');
        p.textContent = text || '';
        bubble.appendChild(p);
        if (citations && citations.length > 0) {
            const list = document.createElement('div');
            list.className = 'citation-list';
            citations.forEach((c, idx) => list.appendChild(buildCitationChip(c, idx)));
            bubble.appendChild(list);
        }
        wrap.appendChild(bubble);
        chatWindow.appendChild(wrap);
        scrollToBottom();
    };

    const buildCitationChip = (c, idx) => {
        const link = document.createElement('a');
        link.className = 'citation-chip';
        link.target = '_blank';
        link.rel = 'noopener';
        const hash = c.page != null ? `#page-${encodeURIComponent(c.page)}` : '';
        link.href = `/document/${encodeURIComponent(c.docId)}${hash}`;
        link.textContent = `[${idx + 1}] ${c.docName || c.docId} · p.${c.page ?? '?'}`;
        return link;
    };

    const renderCitationLinks = (citations) => {
        if (!citationLinks) return;
        citationLinks.innerHTML = '';
        if (!citations || citations.length === 0) {
            citationLinks.innerHTML = '<small class="muted">Nessuna citazione disponibile.</small>';
            return;
        }
        const header = document.createElement('strong');
        header.textContent = 'Citazioni';
        citationLinks.appendChild(header);
        citations.forEach((c, idx) => citationLinks.appendChild(buildCitationChip(c, idx)));
    };

    const renderLoading = () => {
        if (!chatWindow) return null;
        const wrap = document.createElement('div');
        wrap.className = 'message bot';
        wrap.innerHTML = `<div class="bubble"><div class="typing-indicator"><span></span><span></span><span></span></div></div>`;
        chatWindow.appendChild(wrap);
        scrollToBottom();
        return wrap;
    };

    // ─── Document fetching ────────────────────────────────────────
    const fetchDocument = async (docId) => {
        if (documentsCache[docId]) return documentsCache[docId];
        const res = await fetch(`/api/documents/${encodeURIComponent(docId)}`);
        if (!res.ok) throw new Error(`Documento non disponibile (${res.status})`);
        documentsCache[docId] = await res.json();
        return documentsCache[docId];
    };

    // ─── Sessions ─────────────────────────────────────────────────
    const scopeSessionKey = () => `${ACTIVE_SESSION_KEY}:${scopeType}:${scopeId}`;

    const formatDate = (iso) => {
        if (!iso) return '';
        try {
            return new Date(iso).toLocaleString('it-IT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
        } catch (e) { return ''; }
    };

    const fetchSessions = async () => {
        if (!scopeId) return [];
        const res = await fetch(`/api/chat/sessions?scopeType=${encodeURIComponent(scopeType)}&scopeId=${encodeURIComponent(scopeId)}`);
        if (!res.ok) return [];
        return res.json();
    };

    const loadSession = async (sessionId) => {
        activeSessionId = sessionId;
        localStorage.setItem(scopeSessionKey(), sessionId);
        if (chatWindow) chatWindow.innerHTML = '';
        if (citationLinks) citationLinks.innerHTML = '';
        const res = await fetch(`/api/chat/${encodeURIComponent(sessionId)}/history`);
        if (res.ok) {
            const messages = await res.json();
            messages.forEach((m) => renderMessage(m.role, m.content, m.citations || []));
        }
        renderSessionList();
    };

    const renderSessionList = async () => {
        const listEl = document.getElementById('session-list');
        if (!listEl) return;
        const sessions = await fetchSessions();
        listEl.innerHTML = '';
        if (sessions.length === 0) {
            listEl.innerHTML = '<p class="muted">Nessuna sessione ancora.</p>';
        } else {
            sessions.forEach((s) => {
                const item = document.createElement('div');
                item.className = `session-item ${s.id === activeSessionId ? 'active' : ''}`;
                item.innerHTML = `<span class="session-name">${escapeHtml(s.name || 'Chat')}</span><span class="session-date">${escapeHtml(formatDate(s.updatedAt))}</span>`;
                item.addEventListener('click', () => loadSession(s.id));
                listEl.appendChild(item);
            });
        }
        const meta = document.getElementById('session-meta');
        if (meta) meta.textContent = `${sessions.length} sessione${sessions.length === 1 ? '' : 'i'}`;
    };

    const startNewSession = () => {
        activeSessionId = null;
        localStorage.removeItem(scopeSessionKey());
        if (chatWindow) chatWindow.innerHTML = '';
        if (citationLinks) citationLinks.innerHTML = '';
        renderSessionList();
        Toast.info('Nuova sessione. Invia un messaggio per iniziarla.');
    };

    // ─── Send message ─────────────────────────────────────────────
    const sendMessage = async () => {
        if (!userInput) return;
        const text = userInput.value.trim();
        if (!text) return;
        if (!scopeId) {
            Toast.warning('Seleziona prima un documento o un vault.');
            return;
        }

        renderMessage('user', text, []);
        userInput.value = '';
        userInput.disabled = true;
        if (sendBtn) sendBtn.disabled = true;
        const loadingEl = renderLoading();

        try {
            const res = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId: activeSessionId, scopeType, scopeId, message: text }),
            });
            const data = await res.json();
            if (loadingEl) loadingEl.remove();
            if (!res.ok) throw new Error(data.error || `Errore HTTP ${res.status}`);
            activeSessionId = data.sessionId;
            localStorage.setItem(scopeSessionKey(), activeSessionId);
            renderMessage('assistant', data.answer || 'Nessuna risposta.', data.citations || []);
            renderCitationLinks(data.citations || []);
            renderSessionList();
        } catch (e) {
            if (loadingEl) loadingEl.remove();
            renderMessage('assistant', `Errore locale: ${e.message}`, []);
            Toast.error(e.message);
        } finally {
            userInput.disabled = false;
            if (sendBtn) sendBtn.disabled = false;
            userInput.focus();
        }
    };

    // ─── Upload helpers ───────────────────────────────────────────
    const uploadSingleFile = async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const progress = Toast.progress(`Indicizzazione "${file.name}"...`);
        try {
            const res = await fetch('/api/documents', { method: 'POST', body: formData });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
            const fileStatus = (data.files || [])[0];
            if (!fileStatus) throw new Error('Risposta non valida.');
            if (fileStatus.status === 'ERROR') {
                progress.update(`Errore: ${fileStatus.message || ''}`, 'error');
                setTimeout(() => progress.close(), 5000);
                return null;
            }
            progress.update(`"${file.name}" indicizzato`, 'success');
            setTimeout(() => progress.close(), 2500);
            return fileStatus;
        } catch (e) {
            progress.update(`Errore: ${e.message}`, 'error');
            setTimeout(() => progress.close(), 5000);
            return null;
        }
    };

    const uploadVaultFiles = async (files) => {
        if (!scopeId) { Toast.warning('Vault non selezionato.'); return; }
        const progress = Toast.progress(`Indicizzazione di ${files.length} file...`);
        const formData = new FormData();
        files.forEach((f) => formData.append('files', f));
        try {
            const res = await fetch(`/api/vaults/${encodeURIComponent(scopeId)}/documents`, { method: 'POST', body: formData });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
            const ok = (data.files || []).filter(s => s.status !== 'ERROR').length;
            const ko = (data.files || []).filter(s => s.status === 'ERROR').length;
            progress.update(`${ok} indicizzati${ko > 0 ? `, ${ko} con errori` : ''}`, ko > 0 ? 'warning' : 'success');
            setTimeout(() => progress.close(), 3500);
            refreshVault();
            (data.files || []).filter(s => s.status === 'ERROR').forEach(s => Toast.error(`"${s.fileName}": ${s.message}`));
        } catch (e) {
            progress.update(`Errore: ${e.message}`, 'error');
            setTimeout(() => progress.close(), 5000);
        }
    };

    // ─── Document workspace ───────────────────────────────────────
    const wireDocumentWorkspace = () => {
        const uploadForm = document.getElementById('doc-upload-form');
        const docFile = document.getElementById('doc-file');
        const selector = document.getElementById('doc-selector');
        const docTitle = document.getElementById('doc-title');
        const openViewer = document.getElementById('open-viewer-link');

        if (uploadForm) {
            uploadForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!docFile?.files?.length) return;
                const status = await uploadSingleFile(docFile.files[0]);
                if (status?.documentId) window.location.href = `/workspace/document?docId=${encodeURIComponent(status.documentId)}`;
            });
            Drop.attach(uploadForm, {
                accept: ['.pdf', '.html', '.htm'],
                onDrop: async (files) => {
                    const status = await uploadSingleFile(files[0]);
                    if (status?.documentId) window.location.href = `/workspace/document?docId=${encodeURIComponent(status.documentId)}`;
                },
            });
        }

        if (selector) {
            selector.addEventListener('change', () => {
                if (selector.value) window.location.href = `/workspace/document?docId=${encodeURIComponent(selector.value)}`;
            });
        }

        if (scopeId) {
            fetchDocument(scopeId).then((doc) => {
                if (docTitle) docTitle.textContent = doc.document.name;
                if (openViewer) {
                    openViewer.href = `/document/${encodeURIComponent(scopeId)}`;
                    openViewer.style.display = '';
                }
            }).catch(() => {});
        }
    };

    // ─── Vault workspace ──────────────────────────────────────────
    const refreshVault = async () => {
        const docListEl = document.getElementById('vault-doc-list');
        if (!scopeId) return;
        const res = await fetch(`/api/vaults/${encodeURIComponent(scopeId)}`);
        if (!res.ok) return;
        const data = await res.json();
        const titleEl = document.getElementById('vault-title');
        if (titleEl && data.vault) titleEl.textContent = data.vault.name || 'Vault';

        if (!docListEl) return;
        docListEl.innerHTML = '';
        if (!data.documents || data.documents.length === 0) {
            docListEl.innerHTML = '<p class="muted">Nessun documento. Trascina file qui sopra o usa il pulsante "Carica".</p>';
            return;
        }
        const grid = document.createElement('div');
        grid.className = 'doc-grid';
        data.documents.forEach((doc) => {
            const row = document.createElement('div');
            row.className = 'doc-card';
            const sizeKb = (doc.size / 1024).toFixed(1);
            row.innerHTML = `
                <a class="doc-card-main" href="/document/${encodeURIComponent(doc.id)}" target="_blank" rel="noopener">
                    <strong>${escapeHtml(doc.name)}</strong>
                    <div class="meta">${escapeHtml(doc.type)} · ${sizeKb} KB · <span class="badge ${badgeForStatus(doc.status)}">${escapeHtml(doc.status)}</span></div>
                    ${doc.error ? `<div class="error-text">${escapeHtml(doc.error)}</div>` : ''}
                </a>
                <button class="danger remove-doc-btn" data-doc-id="${doc.id}" data-doc-name="${escapeHtml(doc.name)}">Rimuovi</button>
            `;
            grid.appendChild(row);
        });
        docListEl.appendChild(grid);
        docListEl.querySelectorAll('.remove-doc-btn').forEach((btn) => {
            btn.addEventListener('click', async () => {
                const did = btn.dataset.docId;
                const dname = btn.dataset.docName;
                if (!confirm(`Rimuovere "${dname}" dal vault?`)) return;
                const r = await fetch(`/api/vaults/${encodeURIComponent(scopeId)}/documents/${encodeURIComponent(did)}`, { method: 'DELETE' });
                if (r.ok) { Toast.success(`"${dname}" rimosso.`); refreshVault(); }
                else { Toast.error(`Errore rimozione (HTTP ${r.status})`); }
            });
        });
    };

    const badgeForStatus = (status) => {
        const map = { INDEXED: 'badge-success', INDEXING: 'badge-warn', ERROR: 'badge-danger', UPLOADED: 'badge-muted' };
        return map[status] || 'badge-muted';
    };

    const wireVaultWorkspace = () => {
        const uploadForm = document.getElementById('vault-upload-form');
        const filesInput = document.getElementById('vault-files');
        const docListEl = document.getElementById('vault-doc-list');

        if (uploadForm) {
            uploadForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                if (!filesInput?.files?.length) return;
                await uploadVaultFiles(Array.from(filesInput.files));
                filesInput.value = '';
            });
            Drop.attach(uploadForm, {
                accept: ['.pdf', '.html', '.htm'],
                onDrop: (files) => uploadVaultFiles(files),
            });
        }

        if (docListEl) {
            Drop.attach(docListEl, {
                accept: ['.pdf', '.html', '.htm'],
                onDrop: (files) => uploadVaultFiles(files),
            });
        }

        if (scopeId) refreshVault();
    };

    // ─── New chat button ──────────────────────────────────────────
    const wireNewChatButton = () => {
        const newBtn = document.getElementById('new-chat-btn');
        if (newBtn) newBtn.addEventListener('click', startNewSession);
    };

    // ─── Init ─────────────────────────────────────────────────────
    if (sendBtn) sendBtn.addEventListener('click', sendMessage);
    if (userInput) userInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') sendMessage(); });

    if (scopeType === 'DOCUMENT') wireDocumentWorkspace();
    else if (scopeType === 'VAULT') wireVaultWorkspace();
    wireNewChatButton();

    const initSession = async () => {
        if (!scopeId) return;
        const sessions = await fetchSessions();
        const stored = localStorage.getItem(scopeSessionKey());
        const target = sessions.find(s => s.id === stored) || sessions[0];
        if (target) await loadSession(target.id);
        else renderSessionList();
    };
    initSession();
})();

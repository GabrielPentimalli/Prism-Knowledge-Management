(() => {
    const docId = document.body.dataset.docId;
    if (!docId) return;

    const i18n = window.PrismI18n || {};
    const t = (key, params = {}, fallback = key) => (
        typeof i18n.t === 'function' ? i18n.t(key, params, fallback) : fallback
    );
    const isEn = () => (typeof i18n.getLanguage === 'function' ? i18n.getLanguage() : 'it') === 'en';
    const formatDate = (value, options) => (
        typeof i18n.formatDate === 'function'
            ? i18n.formatDate(value, options)
            : new Date(value).toLocaleString('it-IT', options)
    );

    const titleEl = document.getElementById('doc-title');
    const metaEl = document.getElementById('doc-meta');
    const host = document.getElementById('viewer-host');
    const toggle = document.getElementById('viewer-mode-toggle');
    const Highlight = window.PrismHighlight || { flash: () => {} };

    if (toggle) {
        const pageBtn = toggle.querySelector('button[data-mode="pages"]');
        const sourceBtn = toggle.querySelector('button[data-mode="source"]');
        if (pageBtn) {
            pageBtn.title = t('viewer.pages', {}, 'Pagine');
            pageBtn.setAttribute('aria-label', t('viewer.pages', {}, 'Pagine'));
        }
        if (sourceBtn) {
            sourceBtn.title = t('viewer.source', {}, 'Sorgente');
            sourceBtn.setAttribute('aria-label', t('viewer.source', {}, 'Sorgente'));
        }
    }

    let documentMeta = null;
    let pages = [];
    let mode = 'pages';

    const escapeHtml = (v) => String(v ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    const extractParagraphs = (raw) => {
        const s = String(raw || '');
        if (!s.trim()) return [];
        const dehyphen = s.replace(/([a-zA-Zàèéìòù])-\n([a-zàèéìòù])/g, '$1$2');
        let blocks = dehyphen.split(/\n\s*\n+/);
        if (blocks.length === 1 && /\n/.test(blocks[0])) {
            blocks = blocks[0].replace(/([.!?])\n(?=[A-ZÀÈÉÌÒÙ])/g, '$1\n\n').split(/\n\s*\n+/);
        }
        return blocks
            .map(b => b.replace(/\s*\n\s*/g, ' ').replace(/[ \t]+/g, ' ').trim())
            .filter(Boolean);
    };

    const renderPages = () => {
        if (!pages.length) {
            host.innerHTML = `<p class="viewer-empty">${escapeHtml(t('viewer.noExtractablePages', {}, 'Nessuna pagina estraibile da questo documento.'))}</p>`;
            return;
        }
        const total = pages.length;
        const frag = document.createDocumentFragment();
        pages.forEach((page) => {
            const paragraphs = extractParagraphs(page.text);
            const article = document.createElement('article');
            article.className = 'page-section';
            article.id = `page-${page.pageNumber}`;
            article.dataset.page = page.pageNumber;

            const header = document.createElement('header');
            header.className = 'page-section-header';
            const paragraphSuffix = paragraphs.length === 1 ? (isEn() ? '' : 'o') : (isEn() ? 's' : 'i');
            header.innerHTML = `
                <span class="page-num-badge">${escapeHtml(page.pageNumber)}</span>
                <span class="page-num-label">${escapeHtml(t('viewer.page', {}, 'Pagina'))} ${escapeHtml(page.pageNumber)} <span class="page-num-of">${escapeHtml(t('viewer.of', {}, 'di'))} ${escapeHtml(total)}</span></span>
                <span class="page-section-meta">${escapeHtml(t('viewer.paragraphs', { count: paragraphs.length, suffix: paragraphSuffix }, `${paragraphs.length} paragraf${paragraphSuffix}`))}</span>
            `;

            const body = document.createElement('div');
            body.className = 'page-section-body';
            if (paragraphs.length === 0) {
                body.innerHTML = `<p class="page-section-empty">${escapeHtml(t('viewer.noTextOnPage', {}, 'Pagina senza testo estraibile.'))}</p>`;
            } else {
                paragraphs.forEach(p => {
                    const para = document.createElement('p');
                    para.textContent = p;
                    body.appendChild(para);
                });
            }

            article.appendChild(header);
            article.appendChild(body);
            frag.appendChild(article);
        });
        host.replaceChildren(frag);
    };

    const renderSource = () => {
        host.innerHTML = '';
        const wrap = document.createElement('div');
        wrap.className = 'viewer-iframe-wrap';
        wrap.style.height = '78vh';
        const iframe = document.createElement('iframe');
        iframe.id = 'viewer-iframe';
        const pageParam = (documentMeta?.document?.type === 'PDF' && location.hash.startsWith('#page='))
            ? location.hash
            : '';
        iframe.src = `/api/documents/${encodeURIComponent(docId)}/source${pageParam}`;
        wrap.appendChild(iframe);
        host.appendChild(wrap);
    };

    const renderBody = () => {
        if (mode === 'source') renderSource();
        else renderPages();
    };

    const setMode = (newMode) => {
        mode = newMode;
        toggle.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.mode === mode));
        renderBody();
        if (mode === 'pages') applyHashScroll();
    };

    const resolveHashTarget = () => {
        const hash = location.hash;
        if (!hash) return null;
        if (hash.startsWith('#page-')) {
            const pageN = decodeURIComponent(hash.slice('#page-'.length));
            return document.getElementById(`page-${pageN}`);
        }
        if (hash.startsWith('#chunk-')) {
            const chunkId = decodeURIComponent(hash.slice('#chunk-'.length));
            const chunk = (documentMeta?.chunks || []).find(c => c.chunkId === chunkId);
            if (chunk) return document.getElementById(`page-${chunk.pageNumber}`);
        }
        return null;
    };

    const applyHashScroll = () => {
        const target = resolveHashTarget();
        if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            Highlight.flash(target, 2500);
        }
    };

    const load = async () => {
        try {
            const [metaRes, pagesRes] = await Promise.all([
                fetch(`/api/documents/${encodeURIComponent(docId)}`),
                fetch(`/api/documents/${encodeURIComponent(docId)}/pages`),
            ]);
            if (!metaRes.ok) throw new Error(t('viewer.documentUnavailable', { status: metaRes.status }, `Documento non disponibile (${metaRes.status})`));
            documentMeta = await metaRes.json();
            pages = pagesRes.ok ? await pagesRes.json() : [];

            titleEl.textContent = documentMeta.document.name;
            const sizeKb = (documentMeta.document.size / 1024).toFixed(1);
            const indexed = documentMeta.document.indexedAt
                ? formatDate(documentMeta.document.indexedAt, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
                : '—';
            const pagesText = t('viewer.pagesCount', { count: pages.length }, `${pages.length} pagine`);
            const indexedText = t('viewer.indexedAt', { date: indexed }, `indicizzato ${indexed}`);
            metaEl.innerHTML = `${escapeHtml(documentMeta.document.type)} · ${sizeKb} KB · ${escapeHtml(pagesText)} · ${escapeHtml(indexedText)}`;
            renderBody();
            setTimeout(applyHashScroll, 60);
        } catch (e) {
            host.innerHTML = `<p class="error-text">${escapeHtml(e.message)}</p>`;
        }
    };

    toggle.addEventListener('click', (event) => {
        const btn = event.target.closest('button[data-mode]');
        if (btn) setMode(btn.dataset.mode);
    });

    window.addEventListener('hashchange', () => {
        if (mode === 'pages') applyHashScroll();
        else renderSource();
    });

    load();
})();

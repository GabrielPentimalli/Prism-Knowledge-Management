(() => {
    const docId = document.body.dataset.docId;
    if (!docId) return;

    const titleEl = document.getElementById('doc-title');
    const metaEl = document.getElementById('doc-meta');
    const host = document.getElementById('viewer-host');
    const toggle = document.getElementById('viewer-mode-toggle');
    const Highlight = window.PrismHighlight || { flash: () => {} };

    let documentMeta = null;
    let pages = [];
    let mode = 'pages';

    const escapeHtml = (v) => String(v ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    const renderPages = () => {
        if (!pages.length) {
            host.innerHTML = '<p class="muted">Nessuna pagina estraibile da questo documento.</p>';
            return;
        }
        host.innerHTML = '';
        pages.forEach((page) => {
            const section = document.createElement('section');
            section.className = 'page-section';
            section.id = `page-${page.pageNumber}`;
            section.dataset.page = page.pageNumber;
            const header = document.createElement('div');
            header.className = 'page-section-header';
            header.innerHTML = `<span>Pagina ${escapeHtml(page.pageNumber)}</span>`;
            const body = document.createElement('div');
            body.className = 'page-section-body';
            body.textContent = page.text || '';
            section.appendChild(header);
            section.appendChild(body);
            host.appendChild(section);
        });
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
            if (!metaRes.ok) throw new Error(`Documento non disponibile (${metaRes.status})`);
            documentMeta = await metaRes.json();
            pages = pagesRes.ok ? await pagesRes.json() : [];

            titleEl.textContent = documentMeta.document.name;
            const sizeKb = (documentMeta.document.size / 1024).toFixed(1);
            const indexed = documentMeta.document.indexedAt
                ? new Date(documentMeta.document.indexedAt).toLocaleString('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
                : '—';
            metaEl.innerHTML = `${escapeHtml(documentMeta.document.type)} · ${sizeKb} KB · ${pages.length} pagine · indicizzato ${escapeHtml(indexed)}`;
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

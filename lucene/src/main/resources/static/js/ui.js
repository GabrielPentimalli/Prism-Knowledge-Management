(() => {
    const ICON_PLUS = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>';
    const ICON_MINUS = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/></svg>';
    const ICON_CLOSE = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';

    // ─── i18n ────────────────────────────────────────────────────────
    const DICT = {
        it: {
            'common.close': 'Chiudi',
            'common.expand': 'Espandi',
            'common.collapse': 'Comprimi',
            'drop.releaseHere': 'Rilascia qui per caricare',
            'drop.noCompatibleFiles': 'Nessun file compatibile rilasciato (formati supportati: PDF, HTML, DOCX, MD, TXT, ...).',
            'home.creatingVault': 'Creazione vault...',
            'home.vaultCreated': 'Vault "{name}" creato',
            'home.deletingVault': 'Eliminazione "{name}"...',
            'home.vaultDeleted': 'Vault "{name}" eliminato',
            'home.deleteVaultConfirm': 'Eliminare il vault "{name}"? I documenti restano disponibili.',
            'common.errorPrefix': 'Errore',
            'common.previous': 'Precedente',
            'common.next': 'Successiva',
            'onboarding.disk': 'Disco',
            'onboarding.freeMb': '{mb} MB liberi',
            'onboarding.model': 'Modello',
            'onboarding.available': 'disponibile',
            'onboarding.unreachable': 'non raggiungibile',
            'onboarding.error': 'Errore onboarding',
            'settings.saving': 'Salvataggio impostazioni...',
            'settings.saved': 'Impostazioni salvate (LLM riconfigurato a caldo).',
            'settings.noEvents': '(nessun evento registrato)',
            'settings.rowsCount': '{count} righe',
            'settings.deleteAllConfirm': 'Sei sicuro di voler cancellare tutti i dati locali? L\'azione è irreversibile.',
            'settings.deletingAll': 'Cancellazione di tutti i dati locali...',
            'settings.deletedRedirect': 'Dati cancellati. Reindirizzamento all\'onboarding...',
            'search.localRunning': 'Ricerca locale in corso...',
            'search.noResults': 'Nessun risultato trovato.',
            'search.resultsMeta': '{docs} document{i} · {total} occorrenze',
            'search.oneOccurrence': 'occorrenza',
            'search.manyOccurrences': 'occorrenze',
            'search.showAllOccurrences': 'Mostra tutte le occorrenze',
            'search.page': 'Pagina',
            'search.error': 'Errore ricerca',
            'chat.noCitation': 'Nessuna citazione disponibile.',
            'chat.citations': 'Citazioni',
            'chat.documentUnavailable': 'Documento non disponibile ({status})',
            'chat.noSessionsYet': 'Nessuna sessione ancora.',
            'chat.newSessionInfo': 'Nuova sessione. Invia un messaggio per iniziarla.',
            'chat.selectScopeFirst': 'Seleziona prima un documento o un vault.',
            'chat.noAnswer': 'Nessuna risposta.',
            'chat.localError': 'Errore locale: {message}',
            'chat.httpError': 'Errore HTTP {status}',
            'chat.indexingFile': 'Indicizzazione "{name}"...',
            'chat.invalidResponse': 'Risposta non valida.',
            'chat.fileIndexed': '"{name}" indicizzato',
            'chat.vaultNotSelected': 'Vault non selezionato.',
            'chat.indexingMany': 'Indicizzazione di {count} file...',
            'chat.indexingSummary': '{ok} indicizzati{errorsPart}',
            'chat.withErrorsPart': ', {ko} con errori',
            'chat.noDocumentsHint': 'Nessun documento. Trascina file qui sopra o usa il pulsante "Carica".',
            'chat.remove': 'Rimuovi',
            'chat.removeDocConfirm': 'Rimuovere "{name}" dal vault?',
            'chat.removed': '"{name}" rimosso.',
            'chat.removeError': 'Errore rimozione (HTTP {status})',
            'chat.sessionCount': '{count} session{suffix}',
            'chat.chat': 'Chat',
            'viewer.noExtractablePages': 'Nessuna pagina estraibile da questo documento.',
            'viewer.page': 'Pagina',
            'viewer.of': 'di',
            'viewer.paragraphs': '{count} paragraf{suffix}',
            'viewer.noTextOnPage': 'Pagina senza testo estraibile.',
            'viewer.documentUnavailable': 'Documento non disponibile ({status})',
            'viewer.pagesCount': '{count} pagine',
            'viewer.indexedAt': 'indicizzato {date}',
            'viewer.source': 'Sorgente',
            'viewer.pages': 'Pagine'
        },
        en: {
            'common.close': 'Close',
            'common.expand': 'Expand',
            'common.collapse': 'Collapse',
            'drop.releaseHere': 'Drop files here to upload',
            'drop.noCompatibleFiles': 'No compatible file dropped (supported formats: PDF, HTML, DOCX, MD, TXT, ...).',
            'home.creatingVault': 'Creating vault...',
            'home.vaultCreated': 'Vault "{name}" created',
            'home.deletingVault': 'Deleting "{name}"...',
            'home.vaultDeleted': 'Vault "{name}" deleted',
            'home.deleteVaultConfirm': 'Delete vault "{name}"? Documents will remain available.',
            'common.errorPrefix': 'Error',
            'common.previous': 'Previous',
            'common.next': 'Next',
            'onboarding.disk': 'Disk',
            'onboarding.freeMb': '{mb} MB free',
            'onboarding.model': 'Model',
            'onboarding.available': 'available',
            'onboarding.unreachable': 'unreachable',
            'onboarding.error': 'Onboarding error',
            'settings.saving': 'Saving settings...',
            'settings.saved': 'Settings saved (LLM hot-reconfigured).',
            'settings.noEvents': '(no events logged)',
            'settings.rowsCount': '{count} rows',
            'settings.deleteAllConfirm': 'Are you sure you want to delete all local data? This action cannot be undone.',
            'settings.deletingAll': 'Deleting all local data...',
            'settings.deletedRedirect': 'Data deleted. Redirecting to onboarding...',
            'search.localRunning': 'Local search in progress...',
            'search.noResults': 'No results found.',
            'search.resultsMeta': '{docs} document{i} · {total} matches',
            'search.oneOccurrence': 'match',
            'search.manyOccurrences': 'matches',
            'search.showAllOccurrences': 'Show all matches',
            'search.page': 'Page',
            'search.error': 'Search error',
            'chat.noCitation': 'No citations available.',
            'chat.citations': 'Citations',
            'chat.documentUnavailable': 'Document unavailable ({status})',
            'chat.noSessionsYet': 'No sessions yet.',
            'chat.newSessionInfo': 'New session. Send a message to start it.',
            'chat.selectScopeFirst': 'Select a document or a vault first.',
            'chat.noAnswer': 'No answer.',
            'chat.localError': 'Local error: {message}',
            'chat.httpError': 'HTTP error {status}',
            'chat.indexingFile': 'Indexing "{name}"...',
            'chat.invalidResponse': 'Invalid response.',
            'chat.fileIndexed': '"{name}" indexed',
            'chat.vaultNotSelected': 'Vault not selected.',
            'chat.indexingMany': 'Indexing {count} files...',
            'chat.indexingSummary': '{ok} indexed{errorsPart}',
            'chat.withErrorsPart': ', {ko} with errors',
            'chat.noDocumentsHint': 'No documents. Drop files here or use the "Upload" button.',
            'chat.remove': 'Remove',
            'chat.removeDocConfirm': 'Remove "{name}" from vault?',
            'chat.removed': '"{name}" removed.',
            'chat.removeError': 'Remove error (HTTP {status})',
            'chat.sessionCount': '{count} session{suffix}',
            'chat.chat': 'Chat',
            'viewer.noExtractablePages': 'No extractable pages from this document.',
            'viewer.page': 'Page',
            'viewer.of': 'of',
            'viewer.paragraphs': '{count} paragraph{suffix}',
            'viewer.noTextOnPage': 'Page with no extractable text.',
            'viewer.documentUnavailable': 'Document unavailable ({status})',
            'viewer.pagesCount': '{count} pages',
            'viewer.indexedAt': 'indexed {date}',
            'viewer.source': 'Source',
            'viewer.pages': 'Pages'
        }
    };

    const PHRASES_IT_TO_EN = {
        'Home': 'Home',
        'Ricerca': 'Search',
        'Impostazioni': 'Settings',
        'Vault': 'Vault',
        'Documenti': 'Documents',
        'Documenti recenti': 'Recent documents',
        'Nessun vault. Creane uno per iniziare.': 'No vaults. Create one to get started.',
        'Nome del nuovo vault': 'New vault name',
        'Crea vault': 'Create vault',
        'Apri': 'Open',
        'Elimina vault': 'Delete vault',
        'Sezioni vault': 'Vault sections',
        'Torna ai vault': 'Back to vaults',
        'Aggiungi documenti (PDF, HTML, DOCX, MD, TXT, ...)': 'Add documents (PDF, HTML, DOCX, MD, TXT, ...)',
        'Carica': 'Upload',
        'Seleziona un vault dalla home per gestire i documenti.': 'Select a vault from home to manage documents.',
        'Gestisci documenti': 'Manage documents',
        'Seleziona un vault dalla home per iniziare la chat.': 'Select a vault from home to start chatting.',
        'Nuova sessione': 'New session',
        'Comprimi/Espandi': 'Collapse/Expand',
        'Chat vault': 'Vault chat',
        'Cronologia sessioni': 'Session history',
        'Invia': 'Send',
        'Fai una domanda trasversale sul vault...': 'Ask a cross-document question about this vault...',
        'Carica un documento (PDF, HTML, DOCX, MD, TXT, ...)': 'Upload a document (PDF, HTML, DOCX, MD, TXT, ...)',
        'Indicizza': 'Index',
        'Oppure scegli un documento esistente': 'Or choose an existing document',
        '— Seleziona —': '— Select —',
        'Chat documento': 'Document chat',
        'Fai una domanda sul documento...': 'Ask a question about this document...',
        'Apri visualizzatore': 'Open viewer',
        'Pagine': 'Pages',
        'Sorgente': 'Source',
        'Impostazioni locali': 'Local settings',
        'Cartella dati': 'Data folder',
        'Modello AI locale': 'Local AI model',
        'Endpoint AI locale': 'Local AI endpoint',
        'Lingua interfaccia': 'Interface language',
        'Salva impostazioni': 'Save settings',
        'Privacy': 'Privacy',
        'ELIMINA TUTTI I DATI': 'DELETE ALL DATA',
        'Log operazioni locali': 'Local operations log',
        'Benvenuto in PRISM': 'Welcome to PRISM',
        'PRISM è un knowledge manager local-first: i tuoi documenti, i tuoi indici e le tue chat restano sempre sul tuo dispositivo.': 'PRISM is a local-first knowledge manager: your documents, indexes, and chats always stay on your device.',
        'Controllo requisiti locali...': 'Checking local requirements...',
        'Cartella dati locale': 'Local data folder',
        'Modello LLM locale': 'Local LLM model',
        'Endpoint LLM locale': 'Local LLM endpoint',
        'Inizializza PRISM locale': 'Initialize local PRISM',
        'Ricerca locale in corso...': 'Local search in progress...',
        'Filtri': 'Filters',
        'Tipo file': 'File type',
        'Tutti': 'All',
        'Dopo': 'After',
        'Prima': 'Before',
        'Risultati': 'Results',
        'Nessun risultato trovato.': 'No results found.',
        'Errore applicativo': 'Application error',
        'Torna alla Home': 'Back to Home'
    };

    const normalizeLanguage = () => 'it';
    const getLocale = () => 'it-IT';
    const interpolate = (template, params = {}) => String(template).replace(/\{(\w+)\}/g, (_, key) => (
        Object.prototype.hasOwnProperty.call(params, key) ? String(params[key]) : `{${key}}`
    ));

    const translateItalianPhraseToEnglish = (text) => {
        const trimmed = text.trim();
        if (!trimmed) return text;
        if (PHRASES_IT_TO_EN[trimmed]) {
            return text.replace(trimmed, PHRASES_IT_TO_EN[trimmed]);
        }

        const dynamicRules = [
            [/^(\d+)\s+totali?$/i, (m) => `${m[1]} total`],
            [/^(\d+)\s+documenti$/i, (m) => `${m[1]} documents`],
            [/^(\d+)\s+sessioni$/i, (m) => `${m[1]} sessions`],
            [/^(\d+)\s+sessione$/i, (m) => `${m[1]} session`],
            [/^(\d+)\s+righe$/i, (m) => `${m[1]} rows`]
        ];

        for (const [rx, mapper] of dynamicRules) {
            const match = trimmed.match(rx);
            if (match) {
                return text.replace(trimmed, mapper(match));
            }
        }
        return text;
    };

    let currentLanguage = normalizeLanguage(localStorage.getItem('prism-language') || document.documentElement.lang || 'it');

    const t = (key, params = {}, fallback = key) => {
        const lang = normalizeLanguage(currentLanguage);
        const bundle = DICT[lang] || DICT.it;
        const template = bundle[key] ?? fallback;
        return interpolate(template, params);
    };

    const translateTextNodes = (root) => {
        if (!root || normalizeLanguage(currentLanguage) !== 'en') return;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode: (node) => {
                if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
                const parent = node.parentElement;
                if (!parent) return NodeFilter.FILTER_REJECT;
                const tag = parent.tagName;
                if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') return NodeFilter.FILTER_REJECT;
                if (parent.closest('[data-i18n-skip="true"]')) return NodeFilter.FILTER_REJECT;
                return NodeFilter.FILTER_ACCEPT;
            }
        });
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        nodes.forEach((node) => {
            node.nodeValue = translateItalianPhraseToEnglish(node.nodeValue);
        });
    };

    const translateAttributes = (root) => {
        if (!root || normalizeLanguage(currentLanguage) !== 'en') return;
        const elements = [root, ...root.querySelectorAll('*')].filter((n) => n instanceof HTMLElement);
        elements.forEach((el) => {
            ['title', 'aria-label', 'placeholder'].forEach((attr) => {
                const value = el.getAttribute(attr);
                if (!value) return;
                const translated = translateItalianPhraseToEnglish(value);
                if (translated !== value) {
                    el.setAttribute(attr, translated);
                }
            });
        });
    };

    const applyTranslations = (root = document.body) => {
        if (!root || normalizeLanguage(currentLanguage) !== 'en') return;
        translateTextNodes(root);
        translateAttributes(root);
    };

    const setLanguage = (language, opts = {}) => {
        const { persist = true, apply = true } = opts;
        currentLanguage = normalizeLanguage(language);
        document.documentElement.lang = currentLanguage;
        if (persist) localStorage.setItem('prism-language', currentLanguage);
        if (apply && currentLanguage === 'en') applyTranslations(document.body);
        return currentLanguage;
    };

    const formatDate = (value, options) => {
        if (!value) return '';
        try {
            return new Date(value).toLocaleString(getLocale(), options);
        } catch (e) {
            return '';
        }
    };

    window.PrismI18n = {
        t,
        setLanguage,
        getLanguage: () => currentLanguage,
        applyTranslations,
        formatDate
    };

    setLanguage(currentLanguage, { persist: false, apply: false });

    const bootApplyTranslations = () => {
        if (normalizeLanguage(currentLanguage) === 'en') applyTranslations(document.body);
        fetch('/api/settings')
            .then((res) => res.ok ? res.json() : null)
            .then((payload) => {
                const settingsLang = payload?.settings?.language;
                if (!settingsLang) return;
                const normalized = normalizeLanguage(settingsLang);
                if (normalized !== currentLanguage) {
                    setLanguage(normalized, { persist: true, apply: normalized === 'en' });
                } else {
                    setLanguage(normalized, { persist: true, apply: false });
                }
            })
            .catch((error) => {
                console.warn('Unable to sync UI language from settings:', error);
            });
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootApplyTranslations, { once: true });
    } else {
        bootApplyTranslations();
    }

    // ─── Panel collapse (data-toggle) ─────────────────────────────────
    document.addEventListener('click', (event) => {
        const trigger = event.target.closest('[data-toggle]');
        if (!trigger) return;
        const targetId = trigger.dataset.toggle;
        const target = document.getElementById(targetId);
        if (!target) return;
        const collapsed = target.classList.toggle('collapsed-pane');
        if (collapsed) {
            target.dataset.prevDisplay = target.style.display;
            target.style.display = 'none';
            trigger.innerHTML = ICON_PLUS;
            trigger.setAttribute('title', t('common.expand', {}, 'Espandi'));
            trigger.setAttribute('aria-label', t('common.expand', {}, 'Espandi'));
        } else {
            target.style.display = target.dataset.prevDisplay || '';
            trigger.innerHTML = ICON_MINUS;
            trigger.setAttribute('title', t('common.collapse', {}, 'Comprimi'));
            trigger.setAttribute('aria-label', t('common.collapse', {}, 'Comprimi'));
        }
    });

    // ─── Toast notifications ──────────────────────────────────────────
    const ensureContainer = () => {
        let container = document.getElementById('prism-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'prism-toast-container';
            document.body.appendChild(container);
        }
        return container;
    };

    const createToast = (message, type = 'info', persistent = false) => {
        const container = ensureContainer();
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <span class="toast-text"></span>
            <button class="toast-close" type="button" title="${t('common.close', {}, 'Chiudi')}" aria-label="${t('common.close', {}, 'Chiudi')}">${ICON_CLOSE}</button>
        `;
        toast.querySelector('.toast-text').textContent = message;
        toast.querySelector('.toast-close').addEventListener('click', () => dismiss(toast));
        container.appendChild(toast);
        requestAnimationFrame(() => toast.classList.add('show'));

        const dismiss = (el) => {
            if (!el || !el.parentElement) return;
            el.classList.remove('show');
            setTimeout(() => el.remove(), 220);
        };

        if (!persistent) {
            const timeout = type === 'error' ? 6000 : 3500;
            setTimeout(() => dismiss(toast), timeout);
        }

        return {
            update: (newMessage, newType) => {
                toast.querySelector('.toast-text').textContent = newMessage;
                if (newType) {
                    toast.className = `toast toast-${newType} show`;
                }
            },
            close: () => dismiss(toast),
        };
    };

    window.PrismToast = {
        info: (msg) => createToast(msg, 'info'),
        success: (msg) => createToast(msg, 'success'),
        error: (msg) => createToast(msg, 'error'),
        warning: (msg) => createToast(msg, 'warning'),
        progress: (msg) => createToast(msg, 'progress', true),
    };

    // ─── Drag & drop helper ───────────────────────────────────────────
    // Use: PrismDrop.attach(dropZoneElement, { accept: ['.pdf','.html'], onDrop: (files) => ... })
    window.PrismDrop = {
        attach(zone, { accept = [], onDrop } = {}) {
            if (!zone || typeof onDrop !== 'function') return;
            const overlay = document.createElement('div');
            overlay.className = 'drop-overlay';
            overlay.innerHTML = `<div class="drop-overlay-inner">${t('drop.releaseHere', {}, 'Rilascia qui per caricare')}</div>`;
            const parent = zone.style.position === '' || getComputedStyle(zone).position === 'static' ? zone : zone;
            parent.style.position = 'relative';
            parent.appendChild(overlay);

            const isAccepted = (file) => {
                if (accept.length === 0) return true;
                const lower = (file.name || '').toLowerCase();
                return accept.some(ext => lower.endsWith(ext.toLowerCase()));
            };

            let dragCounter = 0;
            zone.addEventListener('dragenter', (e) => {
                e.preventDefault();
                dragCounter++;
                overlay.classList.add('active');
            });
            zone.addEventListener('dragover', (e) => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'copy';
            });
            zone.addEventListener('dragleave', () => {
                dragCounter--;
                if (dragCounter <= 0) {
                    dragCounter = 0;
                    overlay.classList.remove('active');
                }
            });
            zone.addEventListener('drop', (e) => {
                e.preventDefault();
                dragCounter = 0;
                overlay.classList.remove('active');
                const files = Array.from(e.dataTransfer.files || []).filter(isAccepted);
                if (files.length === 0) {
                    window.PrismToast?.warning(t('drop.noCompatibleFiles', {}, 'Nessun file compatibile rilasciato (formati supportati: PDF, HTML, DOCX, MD, TXT, ...).'));
                    return;
                }
                onDrop(files);
            });
        },
    };

    // ─── Citation highlight helper ────────────────────────────────────
    window.PrismHighlight = {
        flash(element, durationMs = 3000) {
            if (!element) return;
            element.classList.remove('highlight', 'highlight-fading');
            void element.offsetWidth; // restart animation
            element.classList.add('highlight');
            setTimeout(() => {
                element.classList.add('highlight-fading');
                setTimeout(() => {
                    element.classList.remove('highlight', 'highlight-fading');
                }, 800);
            }, durationMs);
        },
    };
})();

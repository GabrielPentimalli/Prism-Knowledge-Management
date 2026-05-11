(() => {
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
            trigger.textContent = '+';
            trigger.setAttribute('title', 'Espandi');
        } else {
            target.style.display = target.dataset.prevDisplay || '';
            trigger.textContent = '−';
            trigger.setAttribute('title', 'Comprimi');
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
            <button class="toast-close" type="button" aria-label="Chiudi">×</button>
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
            overlay.innerHTML = `<div class="drop-overlay-inner">Rilascia qui per caricare</div>`;
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
                    window.PrismToast?.warning('Nessun file compatibile rilasciato (formati supportati: PDF, HTML, DOCX, MD, TXT, ...).');
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

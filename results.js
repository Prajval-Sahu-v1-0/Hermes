/**
 * Hermes Results Page JavaScript
 * Handles search, results display, and page interactions
 */

(function () {
    'use strict';

    // =====================================================
    // STATE & CONFIGURATION
    // =====================================================

    const state = {
        query: '',
        results: [],
        page: 0,
        totalPages: 1,
        loading: false,
        filters: {},
        sessionId: null  // Store session ID for filtered pagination
    };

    const config = {
        apiBaseUrl: 'http://127.0.0.1:8080/api/v1',
        storageKey: 'hermes:query',
        pageSize: 10,
        useMockData: false // Using real backend API
    };

    // Mock data for frontend-only development
    const mockCreators = [
        {
            channelId: 'UC_mock_yt',
            channelName: 'YouTube Creator',
            profileImageUrl: 'https://picsum.photos/seed/yt/400/225',
            platform: 'youtube',
            platforms: ['youtube', 'instagram', 'twitter'],
            finalScore: 0.95,
            labels: ['Platform Test', 'YouTube'],
            workSummary: 'Creates engaging tech reviews and tutorials with over 500 videos covering the latest gadgets and software.',
            majorCollab: { name: 'TechLinked', type: 'Video Series' }
        },
        {
            channelId: 'UC_mock_ig',
            channelName: 'Instagram Influencer',
            profileImageUrl: 'https://picsum.photos/seed/ig/400/225',
            platform: 'instagram',
            platforms: ['instagram', 'tiktok'],
            finalScore: 0.92,
            labels: ['Platform Test', 'Instagram'],
            workSummary: 'Fashion and lifestyle content creator known for aesthetic photography and brand partnerships.',
            majorCollab: { name: 'Vogue Magazine', type: 'Campaign' }
        },
        {
            channelId: 'UC_mock_twitch',
            channelName: 'Twitch Streamer',
            profileImageUrl: 'https://picsum.photos/seed/twitch/400/225',
            platform: 'twitch',
            platforms: ['twitch', 'youtube', 'twitter'],
            finalScore: 0.89,
            labels: ['Platform Test', 'Twitch'],
            workSummary: 'Full-time variety streamer with 10K+ hours of live content, specializing in FPS and indie games.',
            majorCollab: { name: 'Ninja', type: 'Co-Stream' }
        },
        {
            channelId: 'UC_mock_twitter',
            channelName: 'Twitter Personality',
            profileImageUrl: 'https://picsum.photos/seed/twitter/400/225',
            platform: 'twitter',
            platforms: ['twitter'],
            finalScore: 0.85,
            labels: ['Platform Test', 'Twitter'],
            workSummary: 'Tech industry commentator and thought leader with viral threads on AI and startups.',
            majorCollab: { name: 'Y Combinator', type: 'AMA Thread' }
        },
        {
            channelId: 'UC_mock_tiktok',
            channelName: 'TikTok Star',
            profileImageUrl: 'https://picsum.photos/seed/tiktok/400/225',
            platform: 'tiktok',
            platforms: ['tiktok', 'instagram'],
            finalScore: 0.82,
            labels: ['Platform Test', 'TikTok'],
            workSummary: 'Short-form comedy and dance content with 2M+ followers and multiple viral videos.',
            majorCollab: { name: 'Charli D\'Amelio', type: 'Duet' }
        },
        {
            channelId: 'UC_mock_reddit',
            channelName: 'Reddit Mod',
            profileImageUrl: 'https://picsum.photos/seed/reddit/400/225',
            platform: 'reddit',
            platforms: ['reddit'],
            finalScore: 0.79,
            labels: ['Platform Test', 'Reddit'],
            workSummary: 'Moderator of r/technology and r/gadgets with expertise in community building.',
            majorCollab: { name: 'Reddit AMA', type: 'Official Event' }
        },
        {
            channelId: 'UC_mock_linkedin',
            channelName: 'LinkedIn Pro',
            profileImageUrl: 'https://picsum.photos/seed/linkedin/400/225',
            platform: 'linkedin',
            platforms: ['linkedin', 'twitter'],
            finalScore: 0.75,
            labels: ['Platform Test', 'LinkedIn'],
            workSummary: 'B2B marketing expert sharing insights on growth strategies and professional networking.',
            majorCollab: { name: 'HubSpot', type: 'Webinar' }
        },
        {
            channelId: 'UC_mock_pinterest',
            channelName: 'Pinterest Curator',
            profileImageUrl: 'https://picsum.photos/seed/pinterest/400/225',
            platform: 'pinterest',
            platforms: ['pinterest', 'instagram'],
            finalScore: 0.72,
            labels: ['Platform Test', 'Pinterest'],
            workSummary: 'Home decor and DIY inspiration with curated boards reaching 500K+ monthly viewers.',
            majorCollab: { name: 'IKEA', type: 'Sponsored Board' }
        },
        {
            channelId: 'UC_mock_generic',
            channelName: 'Generic Platform',
            profileImageUrl: 'https://picsum.photos/seed/generic/400/225',
            platform: 'website',
            finalScore: 0.65,
            labels: ['Platform Test', 'Generic'],
            workSummary: 'Multi-platform content creator with a personal blog and newsletter.',
            majorCollab: null
        }
    ];

    // =====================================================
    // DOM ELEMENTS
    // =====================================================

    const elements = {
        searchInput: document.getElementById('search-input'),
        searchForm: document.getElementById('search-form'),
        searchBtn: document.querySelector('.search-submit-btn'),
        searchIcon: document.querySelector('.search-icon-visible'),
        searchSpinner: document.querySelector('.search-spinner'),
        // Sidebar Filter Elements
        filtersToggle: document.getElementById('filters-toggle'),
        sidebarFilterPanel: document.getElementById('sidebar-filter-panel'),
        queryDisplay: document.getElementById('query-display'),
        resultsCount: document.getElementById('results-count'),
        resultsGrid: document.getElementById('results-grid'),
        loadingState: document.getElementById('loading-state'),
        emptyState: document.getElementById('empty-state'),
        pagination: document.getElementById('pagination'),
        paginationInfo: document.getElementById('pagination-info'),
        prevPage: document.getElementById('prev-page'),
        nextPage: document.getElementById('next-page'),
        sortSelect: document.getElementById('sort-select'),
        sidebar: document.getElementById('sidebar'),
        sidebarCollapseToggle: document.getElementById('sidebar-collapse-toggle'),
        sidebarToggle: document.getElementById('sidebar-toggle'),
        sidebarOverlay: document.getElementById('sidebar-overlay'),
        navItems: document.querySelectorAll('.nav-item:not(.nav-item-expandable)')
    };

    // =====================================================
    // INITIALIZATION
    // =====================================================

    function init() {
        console.log('[Hermes Debug] Init started');
        loadQueryFromStorage();
        bindEvents();
        initFilters();
        initCustomDropdown();

        console.log('[Hermes Debug] Init complete, state.query:', state.query);

        if (state.query) {
            console.log('[Hermes Debug] Query found, calling performSearch');
            performSearch();
        } else {
            console.log('[Hermes Debug] No query, showing empty state');
            showEmptyState();
        }
    }

    function loadQueryFromStorage() {
        // Try sessionStorage first, then URL params
        const storedQuery = sessionStorage.getItem(config.storageKey);
        const urlParams = new URLSearchParams(window.location.search);
        const urlQuery = urlParams.get('q');

        state.query = storedQuery || urlQuery || '';

        if (elements.searchInput && state.query) {
            elements.searchInput.value = state.query;
        }

        // Update display
        if (elements.queryDisplay && state.query) {
            elements.queryDisplay.textContent = `"${state.query}"`;
        }
    }

    function bindEvents() {
        // Search form submission
        if (elements.searchForm) {
            elements.searchForm.addEventListener('submit', (e) => {
                e.preventDefault();
                handleSearchSubmit();
            });
        }

        // Search button click
        if (elements.searchBtn) {
            elements.searchBtn.addEventListener('click', handleSearchSubmit);
        }

        if (elements.searchInput) {
            elements.searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    handleSearchSubmit();
                }
            });
        }

        // Sidebar Filters Toggle
        if (elements.filtersToggle) {
            elements.filtersToggle.addEventListener('click', toggleSidebarFilters);
        }

        // Sidebar Collapse Toggle (Pinterest-style dashboard)
        if (elements.sidebarCollapseToggle) {
            elements.sidebarCollapseToggle.addEventListener('click', toggleSidebarCollapse);
        }

        // Sorting
        if (elements.sortSelect) {
            elements.sortSelect.addEventListener('change', handleSortChange);
        }

        // Pagination
        if (elements.prevPage) {
            elements.prevPage.addEventListener('click', () => changePage(-1));
        }

        if (elements.nextPage) {
            elements.nextPage.addEventListener('click', () => changePage(1));
        }

        // Sidebar (mobile)
        if (elements.sidebarToggle) {
            elements.sidebarToggle.addEventListener('click', toggleSidebar);
        }

        if (elements.sidebarOverlay) {
            elements.sidebarOverlay.addEventListener('click', closeSidebar);
        }

        // Nav items (excluding expandable ones)
        elements.navItems.forEach(item => {
            item.addEventListener('click', handleNavClick);
        });
    }

    // =====================================================
    // SEARCH FUNCTIONALITY
    // =====================================================

    function handleSearchSubmit() {
        const query = elements.searchInput.value.trim();
        if (!query) return;

        state.query = query;
        state.page = 0;

        // Update storage
        sessionStorage.setItem(config.storageKey, query);

        // Update URL without reload
        const url = new URL(window.location);
        url.searchParams.set('q', query);
        window.history.pushState({}, '', url);

        // Update display
        if (elements.queryDisplay) {
            elements.queryDisplay.textContent = `"${query}"`;
        }

        performSearch();
    }

    async function performSearch() {
        if (state.loading) return;

        console.log('[Hermes Debug] Fetching results for:', {
            query: state.query,
            page: state.page,
            pageSize: config.pageSize
        });

        showLoading();

        try {
            // Use mock data if configured (frontend disconnected from backend)
            if (config.useMockData) {
                console.log('[Hermes Debug] Using MOCK DATA (backend disconnected)');

                // Simulate network delay for realistic UX
                await new Promise(resolve => setTimeout(resolve, 500));

                // Filter mock data based on query (simple text matching)
                const query = state.query.toLowerCase();
                const filteredCreators = mockCreators.filter(creator => {
                    const nameMatch = creator.channelName.toLowerCase().includes(query);
                    const labelMatch = creator.labels.some(label => label.toLowerCase().includes(query));
                    // If query is empty or very short, return all
                    if (query.length < 2) return true;
                    return nameMatch || labelMatch;
                });

                // If no matches, return all mock data
                const results = filteredCreators.length > 0 ? filteredCreators : mockCreators;

                const mockResponse = {
                    results: results,
                    totalPages: 1,
                    totalResults: results.length
                };

                console.log('[Hermes Debug] Mock response:', mockResponse);
                handleSearchResults(mockResponse);
                return;
            }

            // Real API call (when useMockData is false)
            const requestBody = {
                platform: 'youtube',  // Default platform
                genre: state.query,
                page: state.page,
                pageSize: config.pageSize
            };
            console.log('[Hermes Debug] Request body:', requestBody);

            const response = await fetch(`${config.apiBaseUrl}/search`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            console.log('[Hermes Debug] HTTP status:', response.status);

            if (!response.ok) {
                throw new Error(`Search failed with status ${response.status}`);
            }

            const data = await response.json();
            console.log('[Hermes Debug] Full API response:', data);
            console.log('[Hermes Debug] data.results exists:', Array.isArray(data.results));
            console.log('[Hermes Debug] data.creators exists:', Array.isArray(data.creators));

            handleSearchResults(data);

        } catch (error) {
            console.error('[Hermes Debug] Search error:', error);
            showError('Unable to connect to the server. Please try again.');
        } finally {
            hideLoading();
        }
    }

    function handleSearchResults(data) {
        // Support both 'results' and 'creators' field names from backend
        const results = data.results || data.creators || [];
        console.log('[Hermes Debug] handleSearchResults called');
        console.log('[Hermes Debug] Results array:', results);
        console.log('[Hermes Debug] Results count:', results.length);

        state.results = results;
        state.totalPages = data.totalPages || 1;

        // Store sessionId for filtered pagination API calls
        if (data.sessionId) {
            state.sessionId = data.sessionId;
            console.log('[Hermes Debug] Session ID stored:', state.sessionId);
        }

        if (state.results.length === 0) {
            console.warn('[Hermes Debug] No results to render, showing empty state');
            showEmptyState();
            return;
        }

        hideEmptyState();
        renderResults();
        updateResultsCount();
        updatePagination();
    }

    // =====================================================
    // RENDERING
    // =====================================================

    function renderResults() {
        console.log('[Hermes Debug] renderResults called');
        console.log('[Hermes Debug] elements.resultsGrid:', elements.resultsGrid);
        console.log('[Hermes Debug] state.results count:', state.results.length);

        if (!elements.resultsGrid) {
            console.error('[Hermes Debug] ERROR: resultsGrid element not found!');
            return;
        }

        // Apply filters to state.results
        const filteredResults = applyFilters(state.results);
        console.log('[Hermes Debug] Filtered results count:', filteredResults.length);

        if (filteredResults.length === 0 && state.results.length > 0) {
            elements.resultsGrid.innerHTML = `
                <div class="no-filter-results">
                    <i class="fas fa-filter"></i>
                    <p>No creators match your current filters</p>
                    <button class="clear-filters-btn" onclick="location.reload()">Clear Filters</button>
                </div>
            `;
            return;
        }

        if (filteredResults.length > 0) {
            console.log('[Hermes Debug] First creator object:', filteredResults[0]);
        }

        elements.resultsGrid.innerHTML = filteredResults.map(creator => createCreatorCard(creator)).join('');

        // Add animation
        const cards = elements.resultsGrid.querySelectorAll('.creator-card');
        console.log('[Hermes Debug] Cards rendered in DOM:', cards.length);
        cards.forEach((card, index) => {
            card.style.animation = `cardEnter 0.4s ease ${index * 0.05}s both`;
        });

        // Add click-to-expand functionality
        attachCardClickListeners();

        // Update results count to show filtered count
        if (elements.resultsCount) {
            elements.resultsCount.textContent = `${filteredResults.length} results found`;
        }
    }

    /**
     * Applies active filters to results based on backend score fields.
     * 
     * Score Field Mapping:
     * - audienceFit → Audience filter (Small/Medium/Large)
     * - engagementQuality → Engagement filter (Low/Medium/High)
     * - activityConsistency → Activity filter (Occasional/Consistent/Very Active)
     * - labels (contains 'Emerging'/'Growing'/'Established'/'Dominant') → Competitiveness
     * - labels → Genre filter (matches genre labels)
     */
    function applyFilters(results) {
        if (!results || results.length === 0) return [];

        const activeFilters = state.filters || {};
        if (Object.keys(activeFilters).length === 0) {
            return results; // No filters active
        }

        console.log('[Hermes Debug] Applying filters:', activeFilters);

        return results.filter(creator => {
            // Get score breakdown from creator
            const score = creator.score || {};
            const audienceFit = score.audienceFit || 0;
            const engagementQuality = score.engagementQuality || 0;
            const activityConsistency = score.activityConsistency || 0;
            const labels = creator.labels || [];

            // Audience filter (Small/Medium/Large) - maps to audienceFit score
            if (activeFilters.audience) {
                const audienceTier = getScoreTier(audienceFit);
                if (!matchesTier(activeFilters.audience, audienceTier)) {
                    return false;
                }
            }

            // Engagement filter (Low/Medium/High) - maps to engagementQuality
            if (activeFilters.engagement) {
                const engagementTier = getScoreTier(engagementQuality);
                if (!matchesTier(activeFilters.engagement, engagementTier)) {
                    return false;
                }
            }

            // Activity filter (Occasional/Consistent/Very Active) - maps to activityConsistency
            if (activeFilters.activity) {
                const activityTier = getActivityTier(activityConsistency);
                if (activeFilters.activity !== activityTier) {
                    return false;
                }
            }

            // Competitiveness filter - check labels for tier
            if (activeFilters.competitiveness) {
                const hasCompetitiveLabel = labels.some(label =>
                    label.toLowerCase() === activeFilters.competitiveness.toLowerCase()
                );
                if (!hasCompetitiveLabel) {
                    return false;
                }
            }

            // Genre filter - check labels
            if (activeFilters.genre) {
                // Genre might be in labels or in description content
                const hasGenreMatch = labels.some(label =>
                    label.toLowerCase().includes(activeFilters.genre.toLowerCase())
                );
                if (!hasGenreMatch) {
                    return false;
                }
            }

            return true; // Passed all filters
        });
    }

    /**
     * Maps a 0-1 score to tier (Low/Medium/High)
     */
    function getScoreTier(score) {
        if (score >= 0.7) return 'High';
        if (score >= 0.4) return 'Medium';
        return 'Low';
    }

    /**
     * Maps activity score to activity tier
     */
    function getActivityTier(score) {
        if (score >= 0.7) return 'Very Active';
        if (score >= 0.4) return 'Consistent';
        return 'Occasional';
    }

    /**
     * Checks if filter value matches the tier
     */
    function matchesTier(filterValue, actualTier) {
        return filterValue.toLowerCase() === actualTier.toLowerCase();
    }

    // =====================================================
    // SCORE TO TIER MAPPING (Qualitative)
    // =====================================================

    function mapScoreToTier(score) {
        if (score >= 0.85) return { text: 'Best Match', class: 'best-match' };
        if (score >= 0.70) return { text: 'Strong Fit', class: 'strong-fit' };
        if (score >= 0.55) return { text: 'Good Potential', class: 'good-potential' };
        return null;
    }

    function generateDescriptor(creator) {
        const labels = creator.labels || [];

        if (labels.includes('High engagement')) return 'Highly engaging content';
        if (labels.includes('Strong genre fit')) return 'Strong genre alignment';
        if (labels.includes('Consistent uploads')) return 'Very active channel';
        if (labels.includes('Growing fast')) return 'Fast growing creator';

        return 'Relevant creator in this genre';
    }

    function createCreatorCard(creator) {
        // Extract data from creator object (matching backend contract)
        const channelId = creator.channelId || '';
        const channelName = creator.channelName || creator.name || creator.channelTitle || 'Unknown Creator';

        // High-res image selection logic
        let profileImageUrl = creator.profileImageUrl || creator.avatarUrl || 'https://via.placeholder.com/400x225';
        if (creator.thumbnails) {
            if (creator.thumbnails.maxres) profileImageUrl = creator.thumbnails.maxres.url;
            else if (creator.thumbnails.high) profileImageUrl = creator.thumbnails.high.url;
            else if (creator.thumbnails.medium) profileImageUrl = creator.thumbnails.medium.url;
            else if (creator.thumbnails.default) profileImageUrl = creator.thumbnails.default.url;
        }

        const platform = creator.platform || 'youtube';
        const finalScore = creator.score?.finalScore || creator.finalScore || 0;
        const labels = creator.labels || [];

        // Map score to qualitative tier
        const tier = mapScoreToTier(finalScore);

        // Generate platform icons HTML for body
        const platforms = creator.platforms || [creator.platform || 'youtube'];
        const platformIconsHtml = platforms.map(p => {
            const platformLower = p.toLowerCase();
            let iconClass = 'fas fa-globe';

            if (platformLower === 'youtube') iconClass = 'fab fa-youtube';
            else if (platformLower === 'instagram') iconClass = 'fab fa-instagram';
            else if (platformLower === 'twitch') iconClass = 'fab fa-twitch';
            else if (platformLower === 'twitter' || platformLower === 'x') iconClass = 'fab fa-twitter';
            else if (platformLower === 'tiktok') iconClass = 'fab fa-tiktok';
            else if (platformLower === 'reddit') iconClass = 'fab fa-reddit';
            else if (platformLower === 'linkedin') iconClass = 'fab fa-linkedin';
            else if (platformLower === 'pinterest') iconClass = 'fab fa-pinterest';

            return `<i class="${iconClass} platform-icon"></i>`;
        }).join('');

        // Generate platform chips with text for hover overlay (clickable links)
        const platformChipsHtml = platforms.map(p => {
            const platformLower = p.toLowerCase();
            let iconClass = 'fas fa-globe';
            let displayName = p.charAt(0).toUpperCase() + p.slice(1).toLowerCase();
            let platformUrl = '#';

            if (platformLower === 'youtube') {
                iconClass = 'fab fa-youtube';
                platformUrl = `https://youtube.com/channel/${channelId}`;
            } else if (platformLower === 'instagram') {
                iconClass = 'fab fa-instagram';
                platformUrl = `https://instagram.com/${encodeURIComponent(channelName.replace(/\s+/g, ''))}`;
            } else if (platformLower === 'twitch') {
                iconClass = 'fab fa-twitch';
                platformUrl = `https://twitch.tv/${encodeURIComponent(channelName.replace(/\s+/g, '').toLowerCase())}`;
            } else if (platformLower === 'twitter' || platformLower === 'x') {
                iconClass = 'fab fa-twitter';
                displayName = 'Twitter';
                platformUrl = `https://twitter.com/${encodeURIComponent(channelName.replace(/\s+/g, ''))}`;
            } else if (platformLower === 'tiktok') {
                iconClass = 'fab fa-tiktok';
                platformUrl = `https://tiktok.com/@${encodeURIComponent(channelName.replace(/\s+/g, '').toLowerCase())}`;
            } else if (platformLower === 'reddit') {
                iconClass = 'fab fa-reddit';
                platformUrl = `https://reddit.com/user/${encodeURIComponent(channelName.replace(/\s+/g, ''))}`;
            } else if (platformLower === 'linkedin') {
                iconClass = 'fab fa-linkedin';
                platformUrl = `https://linkedin.com/search/results/all/?keywords=${encodeURIComponent(channelName)}`;
            } else if (platformLower === 'pinterest') {
                iconClass = 'fab fa-pinterest';
                platformUrl = `https://pinterest.com/${encodeURIComponent(channelName.replace(/\s+/g, '').toLowerCase())}`;
            }

            return `<a href="${platformUrl}" target="_blank" rel="noopener" class="platform-chip"><i class="${iconClass}"></i> ${displayName}</a>`;
        }).join('');

        // Generate descriptor (qualitative summary)
        const descriptor = typeof generateDescriptor === 'function'
            ? generateDescriptor(creator)
            : 'Relevant creator in this genre';

        // Work summary and collaboration data
        const workSummary = creator.workSummary || 'Content creator with a growing audience.';
        const majorCollab = creator.majorCollab;
        const collabHtml = majorCollab
            ? `<div class="collab-badge"><i class="fas fa-handshake"></i> <strong>${escapeHtml(majorCollab.name)}</strong> <span class="collab-type">${escapeHtml(majorCollab.type)}</span></div>`
            : '';

        return `
            <article class="creator-card" data-channel-id="${channelId}">
                <div class="creator-image">
                    <img src="${escapeHtml(profileImageUrl)}" alt="${escapeHtml(channelName)}" loading="lazy">
                </div>

                <div class="creator-body">
                    <div class="creator-header-group">
                        <div class="platform-icons-row">
                            ${platformIconsHtml}
                        </div>
                        <h3 class="creator-name">${escapeHtml(channelName)}</h3>
                    </div>
                    ${tier ? `<span class="match-badge ${tier.class}">${tier.text}</span>` : ''}
                    <p class="creator-descriptor">${escapeHtml(descriptor)}</p>
                </div>

                <div class="creator-hover-overlay">
                    <h4 class="hover-title">${escapeHtml(channelName)}</h4>
                    <p class="work-summary">${escapeHtml(workSummary)}</p>
                    <div class="platforms-section">
                        <span class="section-label">Available on</span>
                        <div class="platform-chips">
                            ${platformChipsHtml}
                        </div>
                    </div>
                    ${collabHtml ? `
                    <div class="collab-section">
                        <span class="section-label">Major Collaboration</span>
                        ${collabHtml}
                    </div>
                    ` : ''}
                </div>

                <div class="creator-expand">
                    <div class="label-row">
                        ${labels.slice(0, 4).map(label => `<span class="label">${escapeHtml(label)}</span>`).join('')}
                    </div>

                    <div class="card-actions">
                        <a href="https://youtube.com/channel/${channelId}" target="_blank" rel="noopener" class="btn-outline">
                            View Channel
                        </a>
                        <button class="btn-secondary" data-channel-id="${channelId}" aria-label="Save creator">Save</button>
                    </div>
                </div>
            </article>
        `;
    }

    function showLoading() {
        state.loading = true;

        if (elements.loadingState) {
            elements.loadingState.classList.remove('hidden');
        }

        // Render 20 skeleton cards for better loading feedback (4x5 grid)
        if (elements.resultsGrid) {
            elements.resultsGrid.innerHTML = Array(20).fill(null).map(() => `
                <div class="card-slot">
                    <div class="skeleton-card">
                        <div class="skeleton-image"></div>
                        <div class="skeleton-content">
                            <div class="skeleton-text medium"></div>
                            <div class="skeleton-text short"></div>
                        </div>
                    </div>
                </div>
            `).join('');
        }

        if (elements.searchBtn) {
            elements.searchBtn.disabled = true;
        }
        if (elements.searchIcon) {
            elements.searchIcon.classList.add('hidden');
        }
        if (elements.searchSpinner) {
            elements.searchSpinner.classList.remove('hidden');
        }

        hideEmptyState();
    }

    function hideLoading() {
        state.loading = false;

        if (elements.loadingState) {
            elements.loadingState.classList.add('hidden');
        }

        if (elements.searchBtn) {
            elements.searchBtn.disabled = false;
        }
        if (elements.searchIcon) {
            elements.searchIcon.classList.remove('hidden');
        }
        if (elements.searchSpinner) {
            elements.searchSpinner.classList.add('hidden');
        }
    }

    function showEmptyState() {
        if (elements.emptyState) {
            elements.emptyState.classList.remove('hidden');
        }

        if (elements.loadingState) {
            elements.loadingState.classList.add('hidden');
        }

        if (elements.resultsGrid) {
            elements.resultsGrid.innerHTML = '';
        }

        if (elements.resultsCount) {
            elements.resultsCount.textContent = 'No results';
        }

        if (elements.pagination) {
            elements.pagination.classList.add('hidden');
        }
    }

    function hideEmptyState() {
        if (elements.emptyState) {
            elements.emptyState.classList.add('hidden');
            // Restore original empty state content in case it was replaced by an error message
            elements.emptyState.innerHTML = `
                <div class="empty-icon">
                    <i class="fas fa-search"></i>
                </div>
                <h3>No results found</h3>
                <p>Try adjusting your search or filters</p>
            `;
        }
    }

    function showError(message) {
        if (elements.emptyState) {
            elements.emptyState.innerHTML = `
                <div class="empty-icon">
                    <i class="fas fa-exclamation-triangle"></i>
                </div>
                <h3>Something went wrong</h3>
                <p>${escapeHtml(message)}</p>
            `;
            elements.emptyState.classList.remove('hidden');
        }

        if (elements.resultsCount) {
            elements.resultsCount.textContent = 'Error loading results';
        }
    }

    // =====================================================
    // SIDEBAR FILTERS
    // =====================================================

    // Using the same filter categories as index.html/filters.js
    const filterConfig = {
        categories: [
            { id: 'platform', label: 'Platform', icon: 'fas fa-globe' },
            { id: 'genre', label: 'Genre', icon: 'fas fa-tags' },
            { id: 'audience', label: 'Audience', icon: 'fas fa-users' },
            { id: 'engagement', label: 'Engagement', icon: 'fas fa-chart-line' },
            { id: 'competitiveness', label: 'Competitiveness', icon: 'fas fa-trophy' },
            { id: 'activity', label: 'Activity', icon: 'fas fa-clock' },
            { id: 'style', label: 'Style', icon: 'fas fa-handshake' },
            { id: 'location', label: 'Location', icon: 'fas fa-map-marker-alt' }
        ],
        options: {
            platform: [
                { id: 'YouTube', label: 'YouTube', icon: 'fab fa-youtube' }
            ],
            genre: [
                { id: 'Lifestyle', label: 'Lifestyle' },
                { id: 'Tech', label: 'Tech' },
                { id: 'Fashion', label: 'Fashion' },
                { id: 'Gaming', label: 'Gaming' },
                { id: 'Fitness', label: 'Fitness' },
                { id: 'Art', label: 'Art' }
            ],
            audience: [
                { id: 'Small', label: 'Small' },
                { id: 'Medium', label: 'Medium' },
                { id: 'Large', label: 'Large' }
            ],
            engagement: [
                { id: 'Low', label: 'Low' },
                { id: 'Medium', label: 'Medium' },
                { id: 'High', label: 'High' }
            ],
            competitiveness: [
                { id: 'Emerging', label: 'Emerging', icon: 'fas fa-seedling' },
                { id: 'Growing', label: 'Growing', icon: 'fas fa-chart-line' },
                { id: 'Established', label: 'Established', icon: 'fas fa-star' },
                { id: 'Dominant', label: 'Dominant', icon: 'fas fa-crown' }
            ],
            activity: [
                { id: 'Occasional', label: 'Occasional' },
                { id: 'Consistent', label: 'Consistent' },
                { id: 'Very Active', label: 'Very Active' }
            ],
            style: [
                { id: 'Organic', label: 'Organic' },
                { id: 'Paid', label: 'Paid' },
                { id: 'Revenue-share', label: 'Revenue-share' }
            ],
            location: [
                { id: 'Global', label: 'Global' },
                { id: 'North America', label: 'North America' },
                { id: 'Europe', label: 'Europe' },
                { id: 'Asia', label: 'Asia' },
                { id: 'Other', label: 'Other' }
            ]
        }
    };

    let activeFilterCategory = 'platform';

    function initFilters() {
        // Render options for each category group
        renderAllCategoryOptions();
        bindCategoryHeaderEvents();
        updateActiveFiltersDisplay();
    }

    function renderAllCategoryOptions() {
        const categoryGroups = document.querySelectorAll('.filter-category-group');

        categoryGroups.forEach(group => {
            const categoryId = group.dataset.category;
            const optionsContainer = group.querySelector('.filter-category-options');
            const options = filterConfig.options[categoryId] || [];
            const selectedFilters = state.filters[categoryId] || new Set();

            optionsContainer.innerHTML = `
                <div class="options-list">
                    ${options.map(opt => {
                const isSelected = selectedFilters instanceof Set
                    ? selectedFilters.has(opt.id)
                    : selectedFilters === opt.id;
                return `
                        <div class="filter-option ${isSelected ? 'selected' : ''}" 
                             data-category="${categoryId}" 
                             data-option="${opt.id}">
                            <i class="${isSelected ? 'fas fa-check-square' : 'far fa-square'}"></i>
                            ${opt.icon ? `<i class="${opt.icon} filter-platform-icon"></i>` : ''}
                            ${opt.label}
                        </div>
                    `}).join('')}
                </div>
            `;

            // Bind option click events
            optionsContainer.querySelectorAll('.filter-option').forEach(option => {
                option.addEventListener('click', (e) => {
                    e.stopPropagation();
                    selectFilter(option.dataset.category, option.dataset.option);
                });
            });
        });
    }

    function bindCategoryHeaderEvents() {
        const categoryHeaders = document.querySelectorAll('.filter-category-header');

        categoryHeaders.forEach(header => {
            header.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();

                const group = header.closest('.filter-category-group');
                if (!group) return;

                const isExpanded = group.classList.contains('expanded');

                // Close all other groups (accordion behavior)
                document.querySelectorAll('.filter-category-group.expanded').forEach(g => {
                    if (g !== group) {
                        g.classList.remove('expanded');
                    }
                });

                // Toggle this group
                if (isExpanded) {
                    group.classList.remove('expanded');
                } else {
                    group.classList.add('expanded');
                }
            });
        });
    }

    function selectFilter(category, optionId) {
        // MULTI-SELECT: Toggle selection in a Set
        if (!state.filters[category]) {
            state.filters[category] = new Set();
        }

        if (state.filters[category].has(optionId)) {
            // Deselect if already selected
            state.filters[category].delete(optionId);
            // Clean up empty sets
            if (state.filters[category].size === 0) {
                delete state.filters[category];
            }
        } else {
            // Add to selection
            state.filters[category].add(optionId);
        }

        // Re-render options for this category
        renderCategoryOptions(category);
        updateActiveFiltersDisplay();

        // Call backend /filtered API with multi-select filters
        if (state.sessionId && state.results && state.results.length > 0) {
            performFilteredSearch();
        }
    }

    // Call backend /filtered endpoint with current filters
    async function performFilteredSearch() {
        const sortKey = elements.sortSelect ? elements.sortSelect.value : 'FINAL_SCORE';

        // Build filter query params from Sets
        const filterParams = new URLSearchParams();
        filterParams.set('page', state.currentPage);
        filterParams.set('pageSize', config.pageSize);
        filterParams.set('sortBy', sortKey);

        // Convert each filter Set to comma-separated string
        if (state.filters.audience && state.filters.audience.size > 0) {
            filterParams.set('audience', [...state.filters.audience].map(s => s.toLowerCase()).join(','));
        }
        if (state.filters.engagement && state.filters.engagement.size > 0) {
            filterParams.set('engagement', [...state.filters.engagement].map(s => s.toLowerCase()).join(','));
        }
        if (state.filters.competitiveness && state.filters.competitiveness.size > 0) {
            filterParams.set('competitiveness', [...state.filters.competitiveness].map(s => s.toLowerCase()).join(','));
        }
        if (state.filters.activity && state.filters.activity.size > 0) {
            filterParams.set('activity', [...state.filters.activity].map(s => s.toLowerCase().replace(' ', '_')).join(','));
        }
        if (state.filters.genre && state.filters.genre.size > 0) {
            filterParams.set('genres', [...state.filters.genre].join(','));
        }

        try {
            showLoading();
            const url = `${config.apiBase}/search/session/${state.sessionId}/filtered?${filterParams.toString()}`;
            console.log('[Hermes] Calling filtered API:', url);

            const response = await fetch(url);
            if (!response.ok) throw new Error('Filter request failed');

            const data = await response.json();
            console.log('[Hermes] Filtered response:', data);

            // Update state with filtered results
            state.results = data.results || [];
            state.totalPages = data.totalPages || 1;

            hideLoading();

            if (state.results.length === 0) {
                showNoFilterResultsMessage();
            } else {
                renderResults();
                updateResultsCount();
                updatePagination();
            }
        } catch (error) {
            console.error('[Hermes] Filter error:', error);
            hideLoading();
            showError('Failed to apply filters');
        }
    }

    function showNoFilterResultsMessage() {
        if (elements.resultsGrid) {
            elements.resultsGrid.innerHTML = `
                <div class="no-filter-results">
                    <i class="fas fa-filter"></i>
                    <p>No creators match your current filters</p>
                    <button class="clear-filters-btn" onclick="window.hermesApp.clearAllFilters()">Clear Filters</button>
                </div>
            `;
        }
        if (elements.resultsCount) {
            elements.resultsCount.textContent = '0 results';
        }
    }

    function renderCategoryOptions(categoryId) {
        const group = document.querySelector(`.filter-category-group[data-category="${categoryId}"]`);
        if (!group) return;

        const optionsContainer = group.querySelector('.filter-category-options');
        const options = filterConfig.options[categoryId] || [];
        const selectedFilters = state.filters[categoryId] || new Set();

        optionsContainer.innerHTML = `
            <div class="options-list">
                ${options.map(opt => {
            const isSelected = selectedFilters.has ? selectedFilters.has(opt.id) : selectedFilters === opt.id;
            return `
                    <div class="filter-option ${isSelected ? 'selected' : ''}" 
                         data-category="${categoryId}" 
                         data-option="${opt.id}">
                        <i class="${isSelected ? 'fas fa-check-square' : 'far fa-square'}"></i>
                        ${opt.icon ? `<i class="${opt.icon} filter-platform-icon"></i>` : ''}
                        ${opt.label}
                    </div>
                `}).join('')}
            </div>
        `;

        // Re-bind option click events
        optionsContainer.querySelectorAll('.filter-option').forEach(option => {
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                selectFilter(option.dataset.category, option.dataset.option);
            });
        });
    }

    function updateActiveFiltersDisplay() {
        const activeFiltersContainer = document.getElementById('sidebar-active-filters');
        if (!activeFiltersContainer) return;

        const chips = [];
        Object.keys(state.filters).forEach(category => {
            const options = filterConfig.options[category] || [];
            const selectedSet = state.filters[category];

            // Handle both Set and single value (backwards compatibility)
            if (selectedSet instanceof Set) {
                selectedSet.forEach(optionId => {
                    const option = options.find(o => o.id === optionId);
                    if (option) {
                        chips.push(`
                            <span class="filter-chip" data-category="${category}" data-option="${optionId}">
                                ${option.label}
                                <i class="fas fa-times"></i>
                            </span>
                        `);
                    }
                });
            } else {
                const option = options.find(o => o.id === selectedSet);
                if (option) {
                    chips.push(`
                        <span class="filter-chip" data-category="${category}" data-option="${selectedSet}">
                            ${option.label}
                            <i class="fas fa-times"></i>
                        </span>
                    `);
                }
            }
        });

        activeFiltersContainer.innerHTML = chips.join('');

        // Bind remove events for individual filter chips
        activeFiltersContainer.querySelectorAll('.filter-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                const category = chip.dataset.category;
                const optionId = chip.dataset.option;

                if (state.filters[category] instanceof Set) {
                    state.filters[category].delete(optionId);
                    if (state.filters[category].size === 0) {
                        delete state.filters[category];
                    }
                } else {
                    delete state.filters[category];
                }

                renderCategoryOptions(category);
                updateActiveFiltersDisplay();

                // Re-fetch with updated filters
                if (state.sessionId) {
                    performFilteredSearch();
                }
            });
        });
    }

    // Clear all filters and refresh
    function clearAllFilters() {
        state.filters = {};
        renderAllCategoryOptions();
        updateActiveFiltersDisplay();

        if (state.sessionId) {
            performFilteredSearch();
        }
    }

    // Expose clearAllFilters on window for button onclick
    window.hermesApp = window.hermesApp || {};
    window.hermesApp.clearAllFilters = clearAllFilters;

    function toggleSidebarFilters() {
        if (!elements.filtersToggle || !elements.sidebarFilterPanel) return;

        const isExpanded = elements.filtersToggle.classList.contains('expanded');
        const isSidebarCollapsed = elements.sidebar && elements.sidebar.classList.contains('collapsed');

        // If sidebar is collapsed, expand it first, then open filters
        if (isSidebarCollapsed) {
            // Expand the sidebar first
            elements.sidebar.classList.remove('collapsed');

            // After sidebar expansion animation, open the filter panel
            setTimeout(() => {
                elements.filtersToggle.classList.add('expanded');
                elements.sidebarFilterPanel.classList.add('expanded');
            }, 350); // Wait for CSS transition (300ms) to complete
        } else {
            // Sidebar is already expanded, just toggle filters normally
            if (isExpanded) {
                elements.filtersToggle.classList.remove('expanded');
                elements.sidebarFilterPanel.classList.remove('expanded');
            } else {
                elements.filtersToggle.classList.add('expanded');
                elements.sidebarFilterPanel.classList.add('expanded');
            }
        }
    }

    function handleSortChange() {
        const sortValue = elements.sortSelect?.value || 'relevance';
        console.log('[Hermes Debug] Sort changed to:', sortValue);

        if (!state.results || state.results.length === 0) {
            console.warn('[Hermes Debug] No results to sort');
            return;
        }

        // Sort the results array based on selected option
        const sortedResults = [...state.results].sort((a, b) => {
            switch (sortValue) {
                case 'subscribers':
                    // Sort by subscriber count (descending)
                    const subsA = a.subscriberCount || a.subscribers || 0;
                    const subsB = b.subscriberCount || b.subscribers || 0;
                    return subsB - subsA;

                case 'engagement':
                    // Sort by engagement score (descending)
                    // Use engagementQuality from backend score object or fallback
                    const engageA = a.score?.engagementQuality || a.engagementQuality || a.engagement ||
                        (a.labels?.includes('High engagement') ? 1 : 0);
                    const engageB = b.score?.engagementQuality || b.engagementQuality || b.engagement ||
                        (b.labels?.includes('High engagement') ? 1 : 0);
                    return engageB - engageA;

                case 'recent':
                    // Sort by recent activity
                    // Use upload recency or check for "Recently active" label
                    const recentA = a.lastUploadDate ? new Date(a.lastUploadDate).getTime() :
                        (a.labels?.includes('Recently active') ? Date.now() : 0);
                    const recentB = b.lastUploadDate ? new Date(b.lastUploadDate).getTime() :
                        (b.labels?.includes('Recently active') ? Date.now() : 0);
                    return recentB - recentA;

                case 'relevance':
                default:
                    // Sort by final score (relevance - descending)
                    const scoreA = a.score?.finalScore || a.finalScore || a.score || 0;
                    const scoreB = b.score?.finalScore || b.finalScore || b.score || 0;
                    return scoreB - scoreA;
            }
        });

        // Update state with sorted results
        state.results = sortedResults;

        // Re-render the results
        renderResults();
        console.log('[Hermes Debug] Results re-sorted and rendered');
    }

    // =====================================================
    // CUSTOM DROPDOWN
    // =====================================================

    function initCustomDropdown() {
        const dropdown = document.getElementById('sort-dropdown');
        const trigger = document.getElementById('sort-trigger');
        const menu = document.getElementById('sort-menu');
        const valueDisplay = trigger?.querySelector('.dropdown-value');
        const hiddenSelect = document.getElementById('sort-select');

        if (!dropdown || !trigger || !menu) return;

        // Toggle dropdown on trigger click
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            dropdown.classList.toggle('open');
        });

        // Handle option selection
        menu.querySelectorAll('.dropdown-option').forEach(option => {
            option.addEventListener('click', () => {
                const value = option.dataset.value;
                const text = option.textContent;

                // Update display
                if (valueDisplay) valueDisplay.textContent = text;

                // Update selected state
                menu.querySelectorAll('.dropdown-option').forEach(opt => opt.classList.remove('selected'));
                option.classList.add('selected');

                // Sync hidden select and trigger search
                if (hiddenSelect) {
                    hiddenSelect.value = value;
                    hiddenSelect.dispatchEvent(new Event('change'));
                }

                // Close dropdown
                dropdown.classList.remove('open');
            });
        });

        // Close on click outside
        document.addEventListener('click', (e) => {
            if (!dropdown.contains(e.target)) {
                dropdown.classList.remove('open');
            }
        });
    }

    // =====================================================
    // SIDEBAR COLLAPSE (PINTEREST-STYLE DASHBOARD)
    // =====================================================

    function toggleSidebarCollapse() {
        if (!elements.sidebar) return;
        elements.sidebar.classList.toggle('collapsed');
    }

    // =====================================================
    // SIDEBAR (MOBILE)
    // =====================================================

    function toggleSidebar() {
        if (!elements.sidebar) return;

        const isActive = elements.sidebar.classList.contains('active');

        if (isActive) {
            closeSidebar();
        } else {
            openSidebar();
        }
    }

    function openSidebar() {
        if (elements.sidebar) {
            elements.sidebar.classList.add('active');
        }
        if (elements.sidebarOverlay) {
            elements.sidebarOverlay.classList.add('active');
        }
        document.body.style.overflow = 'hidden';
    }

    function closeSidebar() {
        if (elements.sidebar) {
            elements.sidebar.classList.remove('active');
        }
        if (elements.sidebarOverlay) {
            elements.sidebarOverlay.classList.remove('active');
        }
        document.body.style.overflow = '';
    }

    function handleNavClick(e) {
        const item = e.currentTarget;
        const page = item.dataset.page;

        // Update active state
        elements.navItems.forEach(nav => nav.classList.remove('active'));
        item.classList.add('active');

        // Close mobile sidebar
        closeSidebar();

        // Handle navigation (placeholder for now)
        console.log('Navigate to:', page);
    }

    // =====================================================
    // UTILITIES
    // =====================================================

    function formatNumber(num) {
        if (!num || isNaN(num)) return '0';
        num = parseInt(num, 10);

        if (num >= 1000000000) {
            return (num / 1000000000).toFixed(1).replace(/\.0$/, '') + 'B';
        }
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
        }
        if (num >= 1000) {
            return (num / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
        }
        return num.toString();
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // =====================================================
    // CSS ANIMATION (INJECTED)
    // =====================================================

    const styleSheet = document.createElement('style');
    styleSheet.textContent = `
        @keyframes cardEnter {
            from {
                opacity: 0;
                transform: translateY(16px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
    `;
    document.head.appendChild(styleSheet);

    // =====================================================
    // CARD CLICK-TO-EXPAND
    // =====================================================

    function attachCardClickListeners() {
        const cards = document.querySelectorAll('.creator-card');

        cards.forEach(card => {
            card.addEventListener('click', function (e) {
                // Don't toggle if clicking on a link or button
                if (e.target.closest('a, button')) {
                    return;
                }

                // Remove expanded from all other cards
                cards.forEach(c => {
                    if (c !== card) {
                        c.classList.remove('expanded');
                    }
                });

                // Toggle this card
                card.classList.toggle('expanded');
            });
        });
    }

    // =====================================================
    // RESULTS COUNT AND PAGINATION
    // =====================================================

    function updateResultsCount() {
        if (!elements.resultsCount) return;

        const count = state.results.length;
        const total = state.totalResults || count;
        const text = total === 1 ? '1 result found' : `${total} results found`;
        elements.resultsCount.textContent = text;
    }

    function updatePagination() {
        if (!elements.pagination) return;

        if (state.totalPages <= 1) {
            elements.pagination.classList.add('hidden');
            return;
        }

        elements.pagination.classList.remove('hidden');

        if (elements.paginationInfo) {
            elements.paginationInfo.textContent = `Page ${state.page + 1} of ${state.totalPages}`;
        }

        if (elements.prevPage) {
            elements.prevPage.disabled = state.page === 0;
        }

        if (elements.nextPage) {
            elements.nextPage.disabled = state.page >= state.totalPages - 1;
        }
    }

    function changePage(delta) {
        const newPage = state.page + delta;
        if (newPage < 0 || newPage >= state.totalPages) return;

        state.page = newPage;
        performSearch();

        // Scroll to top of results
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // =====================================================
    // INITIALIZE ON DOM READY
    // =====================================================

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();

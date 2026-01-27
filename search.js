$(function () {
    var app = $("#app"),
        init = $("#init"),
        layer = $("#layer"),
        input = $("#search-input"),
        searchSwitch = $("#search-switch"),
        button = $("#app button[type='submit']");

    // The provided code used $("button"). 
    // To avoid affecting other buttons, I'll be careful or keep it as is if it's meant to be global.
    // Actually, in the HTML I'll add an ID to the search button just in case.

    function toggleApp() {
        app.toggleClass("opened");
        $("body").toggleClass("search-opened");

        // Unified Toggle: Close filter panel if search bar is closed
        if (!app.hasClass("opened")) {
            $("#filter-panel").removeClass("visible");
        }

        if (button.hasClass("shadow")) button.toggleClass("shadow");
        else
            setTimeout(function () {
                button.toggleClass("shadow");
            }, 300);

        if (app.hasClass("opened")) {
            setTimeout(function () {
                input.toggleClass("move-up");
            }, 200);
            setTimeout(function () {
                input.focus();
            }, 500);
        } else
            setTimeout(function () {
                input.toggleClass("move-up").val("");
            }, 200);

        if (!layer.hasClass("sl")) {
            setTimeout(function () {
                layer.addClass("sl");
            }, 800);
        } else
            setTimeout(function () {
                layer.removeClass("sl");
            }, 300);
    }

    layer.on("click", toggleApp);
    init.on("click", toggleApp);

    // Update button selector to be more specific to the search button
    button = $("#app button[type='submit']");

    // --- Search Logic Integration ---
    const form = $("#app form");

    form.on("submit", function (e) {
        e.preventDefault();
        const query = input.val().trim();
        if (!query) return;

        // Navigate to results page
        navigateToResults(query);
    });

    /**
     * Navigate to the results page with the search query.
     * Stores query in sessionStorage and redirects.
     */
    function navigateToResults(query) {
        // Show loader while transitioning
        if (window.showLoader) window.showLoader();

        // Store query for the results page
        sessionStorage.setItem('hermes:query', query);

        // Small delay for visual feedback, then navigate
        setTimeout(function () {
            window.location.href = 'results.html?q=' + encodeURIComponent(query);
        }, 300);
    }

    // Expose for external use
    window.navigateToResults = navigateToResults;

    // --- UI Enhancements ---

    // 1. Inline Search Loader
    const searchInput = document.getElementById('search-input');
    const searchBtn = document.getElementById('search-btn');
    const searchIcon = searchBtn ? searchBtn.querySelector('.search-icon') : null;
    const spinnerIcon = searchBtn ? searchBtn.querySelector('.spinner-icon') : null;

    if (searchInput && searchBtn) {
        // Show/Hide button based on input
        searchInput.addEventListener('input', function () {
            if (this.value.trim().length > 0) {
                searchBtn.classList.remove('hidden');
            } else {
                searchBtn.classList.add('hidden');
            }
        });

        // Click to search
        searchBtn.addEventListener('click', function (e) {
            e.preventDefault();
            const query = searchInput.value.trim();
            if (query) {
                navigateToResults(query);
            }
        });
    }

    window.showLoader = function () {
        if (searchIcon && spinnerIcon && searchBtn) {
            // Ensure button is visible during loading
            searchBtn.classList.remove('hidden');
            searchIcon.classList.add('hidden');
            spinnerIcon.classList.remove('hidden');
            searchBtn.disabled = true;
            searchBtn.classList.add('loading');
        }
    };

    window.hideLoader = function () {
        if (searchIcon && spinnerIcon && searchBtn) {
            searchIcon.classList.remove('hidden');
            spinnerIcon.classList.add('hidden');
            searchBtn.disabled = false;
            searchBtn.classList.remove('loading');
            // Hide button again if input is empty
            const inputVal = document.getElementById('search-input').value.trim();
            if (!inputVal) {
                searchBtn.classList.add('hidden');
            }
        }
    };

    // 2. Back-to-Top Button
    const navbar = document.getElementById("navbar");
    const backTopBtn = document.getElementById("back-to-top");

    if (navbar && backTopBtn) {
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) {
                    backTopBtn.classList.add("hidden");
                } else {
                    backTopBtn.classList.remove("hidden");
                }
            },
            { threshold: 0 }
        );

        observer.observe(navbar);

        backTopBtn.addEventListener("click", () => {
            window.scrollTo({ top: 0, behavior: "smooth" });
        });
    }

    // 3. Mobile Navigation Menu
    const mobileMenuBtn = document.getElementById('mobile-menu-btn');
    const mobileNav = document.getElementById('mobile-nav');
    const mobileNavOverlay = document.getElementById('mobile-nav-overlay');
    const mobileNavClose = document.getElementById('mobile-nav-close');
    const mobileNavLinks = document.querySelectorAll('.mobile-nav-links a');

    function openMobileNav() {
        mobileNav.classList.add('active');
        mobileNavOverlay.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeMobileNav() {
        mobileNav.classList.remove('active');
        mobileNavOverlay.classList.remove('active');
        document.body.style.overflow = '';
    }

    if (mobileMenuBtn) {
        mobileMenuBtn.addEventListener('click', openMobileNav);
    }

    if (mobileNavClose) {
        mobileNavClose.addEventListener('click', closeMobileNav);
    }

    if (mobileNavOverlay) {
        mobileNavOverlay.addEventListener('click', closeMobileNav);
    }

    // Close mobile nav when clicking a link
    mobileNavLinks.forEach(link => {
        link.addEventListener('click', closeMobileNav);
    });
});



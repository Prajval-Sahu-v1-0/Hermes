const FilterState = {
    platform: null,
    genre: null,
    audience: null,
    engagement: null,
    activity: null,
    style: null,
    location: null
};

const FilterSteps = [
    {
        key: 'platform',
        label: 'Platform',
        options: ['YouTube', 'Instagram', 'Twitter', 'Twitch', 'Reddit']
    },
    {
        key: 'genre',
        label: 'Genre',
        type: 'text', // Or selection
        options: ['Lifestyle', 'Tech', 'Fashion', 'Gaming', 'Fitness', 'Art']
    },
    {
        key: 'audience',
        label: 'Audience',
        options: ['Small', 'Medium', 'Large']
    },
    {
        key: 'engagement',
        label: 'Engagement',
        options: ['Low', 'Medium', 'High']
    },
    {
        key: 'activity',
        label: 'Activity',
        options: ['Occasional', 'Consistent', 'Very Active']
    },
    {
        key: 'style',
        label: 'Style',
        options: ['Organic', 'Paid', 'Revenue-share']
    },
    {
        key: 'location',
        label: 'Location',
        options: ['Global', 'North America', 'Europe', 'Asia', 'Other']
    }
];

$(function () {
    const $searchContainer = $('#app');
    const $chipsArea = $('#active-filters');

    // We will inject the filter panel into index.html
    const $filterPanel = $('#filter-panel');
    const $filterCategories = $('.filter-categories');
    const $filterOptions = $('.filter-options-content');

    function renderAmazonFilters() {
        let catHtml = '';
        FilterSteps.forEach((step, index) => {
            const activeClass = index === 0 ? 'active' : '';
            catHtml += `<div class="category-item ${activeClass}" data-index="${index}">${step.label}</div>`;
        });
        $filterCategories.html(catHtml);
        renderOptions(0);
    }

    function renderOptions(index) {
        const step = FilterSteps[index];
        let optionsHtml = `<h4 class="options-title">${step.label}</h4><div class="options-grid">`;
        step.options.forEach(opt => {
            const selectedClass = FilterState[step.key] === opt ? 'selected' : '';
            optionsHtml += `<div class="option-pill ${selectedClass}" data-key="${step.key}" data-value="${opt}">${opt}</div>`;
        });
        optionsHtml += `</div>`;
        $filterOptions.html(optionsHtml);
    }

    function updateChips() {
        let html = '';
        Object.keys(FilterState).forEach(key => {
            if (FilterState[key]) {
                html += `<span class="filter-chip" data-key="${key}">${FilterState[key]} <i class="fas fa-times"></i></span>`;
            }
        });
        $chipsArea.html(html);
    }

    $(document).on('click', '.category-item', function () {
        $('.category-item').removeClass('active');
        $(this).addClass('active');
        renderOptions($(this).data('index'));
    });

    $(document).on('click', '.option-pill', function () {
        const key = $(this).data('key');
        const val = $(this).data('value');

        FilterState[key] = val;
        $(this).siblings().removeClass('selected');
        $(this).addClass('selected');

        updateChips();
        console.log('Backend Grading Mapping:', FilterState);
    });

    $(document).on('click', '.filter-chip', function () {
        const key = $(this).data('key');
        FilterState[key] = null;
        updateChips();
        renderAmazonFilters(); // Refresh panel to clear selections
    });

    // Toggle filter panel from the search bar icon (to be added in index.html)
    $(document).on('click', '#filter-toggle-btn', function (e) {
        e.preventDefault();
        e.stopPropagation();
        $filterPanel.toggleClass('visible');
        if ($filterPanel.hasClass('visible')) {
            renderAmazonFilters();
        }
    });
});



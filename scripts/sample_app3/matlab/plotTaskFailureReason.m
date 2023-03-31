function [] = plotTaskFailureReason()

    plotGenericResult(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_for_failed');
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'for_failed');
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 'for_failed');
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'for_failed');
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 'for_failed');

    plotGenericResult(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_for_failed');
    plotGenericResult(1, 11, {'Failed Task due to Mobility';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'for_failed');
    plotGenericResult(1, 11, {'Failed Task due to Mobility';'for Health App (%)'}, 'HEALTH_APP', 'for_failed');
    plotGenericResult(1, 11, {'Failed Task due to Mobility';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'for_failed');
    plotGenericResult(1, 11, {'Failed Task due to Mobility';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 'for_failed');

    plotGenericResult(1, 4, 'Failed Tasks due to Network failure (%)', 'ALL_APPS', 'percentage_for_failed');
    plotGenericResult(1, 4, {'Failed Tasks due to Network failure';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'for_failed');
    plotGenericResult(1, 4, {'Failed Tasks due to Network failure';'for Health App (%)'}, 'HEALTH_APP', 'for_failed');
    plotGenericResult(1, 4, {'Failed Tasks due to Network failure';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'for_failed');
    plotGenericResult(1, 4, {'Failed Tasks due to Network failure';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 'for_failed');
end
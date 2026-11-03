package at.eepy.tab.tablist;

import at.eepy.tab.animation.AnimationEngine;
import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.rank.RankService;
import at.eepy.tab.render.PlaceholderService;
import at.eepy.tab.render.TabRenderer;
import net.kyori.adventure.text.Component;

/**
 * Everything derived from one configuration. Swapped as a whole on reload, so
 * a render never mixes pieces of an old and a new config.
 *
 * @param listTemplate  player list format with static effects already expanded
 * @param vanishMarker  parsed {@code vanish.list-marker}
 */
public record TabSetup(PluginConfig config, AnimationEngine animations, PlaceholderService placeholders,
                       TabRenderer renderer, RankService ranks, String listTemplate, Component vanishMarker) {
}
